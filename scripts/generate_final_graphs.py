import argparse
import requests
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import datetime, timedelta
import os
import sys

# ----------------------------------------------------------------
# [설정]
# ----------------------------------------------------------------
PROMETHEUS_URL = "http://localhost:9090"
LOOKBACK_SECONDS = 180
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(BASE_DIR)
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "results")

# 그래프 스타일
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['font.size'] = 11
plt.rcParams['axes.labelsize'] = 12
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['legend.fontsize'] = 11
plt.rcParams['lines.linewidth'] = 2.5

if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)

def fetch_metric(query, start, end, step='2s'):
    try:
        response = requests.get(f"{PROMETHEUS_URL}/api/v1/query_range", params={
            'query': query,
            'start': start.timestamp(),
            'end': end.timestamp(),
            'step': step
        })
        response.raise_for_status()
        data = response.json()['data']['result']
        if not data: return pd.DataFrame(columns=['time', 'value'])
        values = data[0]['values']
        df = pd.DataFrame(values, columns=['time', 'value'])
        df['time'] = pd.to_datetime(df['time'], unit='s')
        df['value'] = pd.to_numeric(df['value'])
        return df
    except Exception as e:
        print(f"❌ Query Error: {e}")
        return pd.DataFrame(columns=['time', 'value'])

def generate_graph(target):
    end_time = datetime.now()
    start_time = end_time - timedelta(seconds=LOOKBACK_SECONDS)

    config = {
        'A': {'file': 'fig_scenario_a_final.png', 'title': 'Scenario A: User Limit Enforcement'},
        'B': {'file': 'fig_scenario_b_final.png', 'title': 'Scenario B: Global Service Protection'},
        'C': {'file': 'fig_scenario_c_final.png', 'title': 'Scenario C: Dual-Layer Defense Strategy'},
        'FAIL': {'file': 'fig_fail_open_final.png', 'title': 'Resilience Test: Fail-Open Verification'}
    }

    if target not in config:
        print(f"❌ Unknown target: {target}")
        return

    cfg = config[target]
    print(f"📊 Generating Graph for [{target}] ({start_time.strftime('%H:%M:%S')} ~ {end_time.strftime('%H:%M:%S')})...")

    q_allowed = 'sum(rate(ratelimiter_request_total{result="allowed"}[15s]))'
    q_user = 'sum(rate(ratelimiter_request_total{result="blocked", type="user"}[15s]))'
    q_global = 'sum(rate(ratelimiter_request_total{result="blocked", type="global"}[15s]))'
    q_fail = 'sum(rate(ratelimiter_failure_total[15s]))'

    # Fetch
    df_allowed = fetch_metric(q_allowed, start_time, end_time)
    df_user = fetch_metric(q_user, start_time, end_time)
    df_global = fetch_metric(q_global, start_time, end_time)

    # Plot
    plt.figure(figsize=(10, 6))

    if not df_allowed.empty:
        plt.plot(df_allowed['time'], df_allowed['value'], label='Allowed (HTTP 200)', color='#2ca02c')

    if target in ['A', 'C']:
        if not df_user.empty and df_user['value'].max() > 0:
            plt.plot(df_user['time'], df_user['value'], label='Blocked (User Limit)', color='#f1c40f')

    if target in ['B', 'C']:
        if not df_global.empty and df_global['value'].max() > 0:
            plt.plot(df_global['time'], df_global['value'], label='Blocked (Global Limit)', color='#d62728', linestyle='--')

    if target == 'FAIL':
        if not df_user.empty:
             plt.plot(df_user['time'], df_user['value'], label='Blocked', color='#d62728', linestyle='--')

        df_fail_metric = fetch_metric(q_fail, start_time, end_time)
        if not df_fail_metric.empty and df_fail_metric['value'].max() > 0:
             plt.plot(df_fail_metric['time'], df_fail_metric['value'], label='Redis Failure', color='black')

    plt.title(cfg['title'], pad=15, fontweight='bold')
    plt.ylabel('Requests Per Second (RPS)')
    plt.xlabel('Time (HH:mm:ss)')
    plt.gca().xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S'))
    plt.legend(frameon=True, framealpha=0.9, facecolor='white', loc='upper left')
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.tight_layout()

    filepath = os.path.join(OUTPUT_DIR, cfg['file'])
    plt.savefig(filepath, dpi=300)
    print(f"   ✅ Saved: {filepath}")
    plt.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", type=str, required=True, help="Target Scenario: A, B, C, or FAIL")
    args = parser.parse_args()

    generate_graph(args.target)
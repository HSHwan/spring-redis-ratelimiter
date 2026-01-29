import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import os

# ----------------------------------------------------------------
# [경로 설정] 스크립트 위치 기준 상대 경로 계산
# ----------------------------------------------------------------
# 현재 스크립트가 있는 폴더 (scripts/)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# 프로젝트 루트 (project-root/)
PROJECT_ROOT = os.path.dirname(BASE_DIR)
# 결과 폴더 (results/)
RESULTS_DIR = os.path.join(PROJECT_ROOT, 'results')

# ----------------------------------------------------------------
# [설정] 그래프 스타일
# ----------------------------------------------------------------
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['font.size'] = 11
plt.rcParams['axes.labelsize'] = 12
plt.rcParams['axes.titlesize'] = 14
plt.rcParams['legend.fontsize'] = 11
plt.rcParams['lines.linewidth'] = 2

def create_graph(csv_filename, output_filename, title, show_error=False):
    # 전체 경로 생성
    csv_path = os.path.join(RESULTS_DIR, csv_filename)
    output_path = os.path.join(RESULTS_DIR, output_filename)

    if not os.path.exists(csv_path):
        print(f"⚠️ 파일 없음: {csv_filename} (경로: {csv_path})")
        return

    print(f"📈 처리 중: {title}...")

    try:
        df = pd.read_csv(csv_path)

        # 전처리
        df['timestamp'] = pd.to_datetime(df['timestamp'], unit='s')
        reqs = df[df['metric_name'] == 'http_req_duration'].copy()

        if 'status' not in reqs.columns:
            return

        reqs['status'] = reqs['status'].fillna(0).astype(int)

        # RPS 집계
        reqs['sec'] = reqs['timestamp'].dt.floor('s')
        rps_df = reqs.groupby(['sec', 'status']).size().unstack(fill_value=0)

        # 그래프 그리기
        plt.figure(figsize=(8, 5))

        if 200 in rps_df.columns:
            plt.plot(rps_df.index, rps_df[200], label='Allowed (HTTP 200)', color='#2ca02c')

        if 429 in rps_df.columns:
            plt.plot(rps_df.index, rps_df[429], label='Blocked (HTTP 429)', color='#d62728', linestyle='--')

        if show_error and 500 in rps_df.columns:
            plt.plot(rps_df.index, rps_df[500], label='Error (HTTP 500)', color='black')

        plt.title(title, pad=15, fontweight='bold')
        plt.ylabel('Requests Per Second (RPS)')
        plt.xlabel('Time (mm:ss)')
        plt.gca().xaxis.set_major_formatter(mdates.DateFormatter('%M:%S'))

        plt.legend(frameon=True, framealpha=0.9, facecolor='white', loc='upper left')
        plt.tight_layout()

        # results 폴더에 저장
        plt.savefig(output_path, dpi=300)
        plt.close()

    except Exception as e:
        print(f"❌ 에러 발생 ({csv_filename}): {e}")

# ----------------------------------------------------------------
# 실행
# ----------------------------------------------------------------

create_graph('result_user.csv', 'fig_scenario_a.png', 'Scenario A: User Limit Enforcement')
create_graph('result_global.csv', 'fig_scenario_b.png', 'Scenario B: Global Service Protection')
create_graph('result_dual.csv', 'fig_scenario_c.png', 'Scenario C: Dual-Layer Defense Strategy')
create_graph('result_fail_open.csv', 'fig_fail_open.png', 'Resilience Test: Fail-Open Verification', show_error=True)

print("🎉 그래프 생성 완료!")
import pandas as pd

# Load data and filter for relevant functions (OnMultLine vs parallels)
df = pd.read_csv('cpp_results.csv')
df = df[df['functionType'].isin(['OnMultLine', 'OnMultLineParallel1', 'OnMultLineParallel2'])]

# Group by function, matrix size, and threads
grouped = df.groupby(['functionType', 'MatrixSize', 'Threads'])

results = []
for name, group in grouped:
    func_name, size, threads = name[0], name[1], name[2]
    avg_time = round(group['Real Time'].mean(), 6)  
    
    # Skip sequential functions in final output
    if "Parallel" not in func_name:
        continue
    
    # Get sequential baseline (OnMultLine)
    seq_group = df[(df['MatrixSize'] == size) & (df['functionType'] == 'OnMultLine')]
    seq_avg_time = round(seq_group['Real Time'].mean(), 6)  
    
    # Compute metrics (all rounded to 6 decimals)
    speedup = round(seq_avg_time / avg_time, 6) if avg_time and seq_avg_time else None
    efficiency = round(speedup / threads, 6) if speedup and threads else None
    mflops = round((2 * (size**3)) / (avg_time * 1e6), 6) if avg_time else None
    
    results.append({
        'Function': func_name,
        'MatrixSize': size,
        'Threads': threads,
        'AvgTime': avg_time,
        'MFlops': mflops,
        'Speedup': speedup,
        'Efficiency': efficiency
    })

# Save to CSV
results_df = pd.DataFrame(results)
results_df.to_csv('parallel_line_analysis.csv', index=False)
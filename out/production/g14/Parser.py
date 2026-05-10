import csv
import os
import re

#g++ -O2 -fopenmp -lpapi Proj1.cpp -o Proj1 && ./Proj1 > cpp_output.txt
#javac Proj1.java && java Proj1 > java_output.txt

# File paths
cpp_input_file = 'cpp_output.txt'
java_input_file = 'java_output.txt'

cpp_csv_file = 'cpp_results.csv'
java_csv_file = 'java_results.csv'

# CSV column headers
fieldnames_cpp = ['functionType', 'L2 DCM', 'L1 DCM', 'MatrixSize', 'BlockSize', 'Real Time', 'Threads']
fieldnames_java = ['functionType', 'MatrixSize', 'BlockSize', 'Real Time']

def create_cpp_csv_file(csv_filename):
    with open(csv_filename, mode='w', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames_cpp)
        writer.writeheader()
    print(f'Created fresh CSV file: {csv_filename}')

def create_java_csv_file(csv_filename):
    """Always create a fresh Java CSV file."""
    with open(csv_filename, mode='w', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames_java)
        writer.writeheader()
    print(f'Created fresh Java CSV file: {csv_filename}')

def parse_cpp_output(input_file, csv_filename):
    """Parse the program output and write to CSV."""
    if not os.path.exists(input_file):
        print(f'File not found: {input_file}')
        return

    current_function = None
    thread_count = -1
    l1_dcm = l2_dcm = "N/A"

    with open(input_file, 'r') as infile, open(csv_filename, mode='a', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames_cpp)

        for line in infile:
            line = line.strip()

            # Detect which function type we are parsing
            if "BASIC MULTIPLICATION" in line:
                current_function = 'OnMult'
                thread_count = 1 
            elif "LINE MULTIPLICATION PARALLEL 1" in line:
                current_function = 'OnMultLineParallel1'
            elif "LINE MULTIPLICATION PARALLEL 2" in line:
                current_function = 'OnMultLineParallel2'
            elif "LINE MULTIPLICATION" in line:
                current_function = 'OnMultLine'
                thread_count = 1 
            elif "BLOCK MULTIPLICATION" in line:
                current_function = 'OnMultBlock'
                thread_count = 1 


            # Extract matrix size and block size
            matrix_block_match = re.match(r'Matrix Size: (\d+)x(\d+)(?: \| Block Size: (\d+))?', line)
            if matrix_block_match:
                matrix_size = matrix_block_match.group(1)
                block_size = matrix_block_match.group(3) if matrix_block_match.group(3) else "N/A"
                continue    

            # Extract time
            time_match = re.match(r'Time: ([\d\.]+) seconds', line)
            if time_match and current_function:
                real_time = time_match.group(1)
            
            # Extract thread count
            thread_match = re.search(r'Threads: (\d+)', line)
            if thread_match:
                thread_count = thread_match.group(1)

            # Look for L1 and L2 DCM values
            l1_l2_match = re.search(r'L1 DCM: (\d+)\s+\|\s+L2 DCM: (\d+)', line)
            if l1_l2_match:
                l1_dcm = l1_l2_match.group(1)
                l2_dcm = l1_l2_match.group(2)
                
                # Build the entry
                entry = {
                    'functionType': current_function,
                    'L2 DCM': l2_dcm,
                    'L1 DCM': l1_dcm,
                    'MatrixSize': matrix_size,
                    'BlockSize': block_size,
                    'Threads': thread_count,
                    'Real Time': real_time
                }

                # Write to CSV
                writer.writerow(entry)
                print(f"Added entry: {entry}")

def parse_java_output(input_file, csv_filename):
    """Parse the Java program output and write to CSV."""
    if not os.path.exists(input_file):
        print(f'File not found: {input_file}')
        return

    current_function = None
    entries = []

    with open(input_file, 'r') as infile:
        for line in infile:
            line = line.strip()

            lower_line = line.lower()
            if "basic multiplication" in lower_line:
                current_function = 'OnMult'
                continue
            elif "line multiplication" in lower_line:
                current_function = 'OnMultLine'
                continue
            elif "block multiplication" in lower_line:
                current_function = 'OnMultBlock'
                continue

            matrix_block_match = re.match(r'Matrix Size: (\d+)x(\d+)(?: \| Block Size: (\d+))?', line)
            if matrix_block_match:
                matrix_size = matrix_block_match.group(1)
                block_size = matrix_block_match.group(3) if matrix_block_match.group(3) else "N/A"
                continue

            time_match = re.match(r'Time: ([\d\.]+) seconds', line)
            if time_match and current_function:
                real_time = time_match.group(1)

                entry = {
                    'functionType': current_function,
                    'MatrixSize': matrix_size,
                    'BlockSize': block_size,
                    'Real Time': real_time
                }

                entries.append(entry)

    with open(csv_filename, mode='a', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames_java)
        for entry in entries:
            writer.writerow(entry)
            print(f"Added Java entry: {entry}")


def main():
    print("=== Parsing Results and Writing CSV ===")

    create_cpp_csv_file(cpp_csv_file)
    create_java_csv_file(java_csv_file)

    parse_cpp_output(cpp_input_file, cpp_csv_file)  
    parse_java_output(java_input_file, java_csv_file)  

    print("Parsing complete!")


if __name__ == '__main__':
    main()

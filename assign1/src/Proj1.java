import java.util.Scanner;

public class Proj1 {
    public static void main(String[] args) {

        //automatedTests();

        Scanner scanner = new Scanner(System.in);
        int option;

        do {
            System.out.println("\n1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Block Multiplication");
            System.out.print("Selection?: ");
            option = scanner.nextInt();

            if (option == 0) break;

            System.out.print("Dimensions: lins=cols ? ");
            int size = scanner.nextInt();
                
            switch (option) {
                case 1:
                    OnMult(size);
                    break;
                case 2:
                    OnMultLine(size);
                    break;
                case 3:
                    System.out.print("Block Size? ");
                    int blockSize = scanner.nextInt();
                    OnMultBlock(size, blockSize);
                    break;
            }
        } while (option != 0);
            
        scanner.close();
    }

    /*private static void automatedTests() {
        int[] blockSizes = {128, 256, 512};

        System.out.println("========== BASIC MULTIPLICATION (600 -> 3000, step 400) ==========");
        for (int size = 600; size <= 3000; size += 400) {
            for (int i = 0; i < 5; i++) {
                System.out.println("Matrix Size: " + size + "x" + size);
                OnMult(size);
            }
        }

        System.out.println("\n========== LINE MULTIPLICATION (600 -> 3000, step 400) ==========");
        for (int size = 600; size <= 3000; size += 400) {
            for (int i = 1; i <= 5; i++) {
                System.out.println("Matrix Size: " + size + "x" + size);
                OnMultLine(size);
            }
        }

        System.out.println("\n========== BLOCK MULTIPLICATION (600 -> 3000, step 400) ==========");
        for (int size = 600; size <= 3000; size += 400) {
            for (int blockSize : blockSizes) {
                for (int i = 1; i <= 5; i++) {
                    System.out.println("Matrix Size: " + size + "x" + size + " | Block Size: " + blockSize);
                    OnMultBlock(size, blockSize);
                }
            }
        }
    }*/

    private static void OnMult(int size) {
        double[] A = new double[size * size];
        double[] B = new double[size * size];
        double[] C = new double[size * size];

        initializeMatrices(A, B, size);
        long startTime = System.nanoTime();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += A[i * size + k] * B[k * size + j];
                }
                C[i * size + j] = sum;
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %f seconds\n", (endTime - startTime) / 1.0e9);
    }

    private static void OnMultLine(int size) {
        double[] A = new double[size * size];
        double[] B = new double[size * size];
        double[] C = new double[size * size];

        initializeMatrices(A, B, size);
        long startTime = System.nanoTime();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double temp = A[i * size + j];
                for (int k = 0; k < size; k++) {
                    C[i * size + k] += temp * B[j * size + k];
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %f seconds\n", (endTime - startTime) / 1.0e9);
    }

    private static void OnMultBlock(int size, int blockSize) {
        double[] A = new double[size * size];
        double[] B = new double[size * size];
        double[] C = new double[size * size];

        initializeMatricesandBlock(A, B, C, size);
        long startTime = System.nanoTime();

        for (int i = 0; i < size; i += blockSize) {
            for (int j = 0; j < size; j += blockSize) {
                for (int k = 0; k < size; k += blockSize) {
                    for (int ii = i; ii < Math.min(i + blockSize, size); ii++) {
                        for (int jj = j; jj < Math.min(j + blockSize, size); jj++) {
                            double temp = 0.0;
                            for (int kk = k; kk < Math.min(k + blockSize, size); kk++) {
                                temp += A[ii * size + kk] * B[kk * size + jj];
                            }
                            C[ii * size + jj] += temp;
                        }
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %f seconds\n", (endTime - startTime) / 1.0e9);
    }

    private static void initializeMatrices(double[] A, double[] B, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                A[i * size +j] = 1.0;
                B[i * size +j] = i + 1.0;
            }
        }
    }

    private static void initializeMatricesandBlock(double[] A, double[] B, double[] C, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                A[i * size +j] = 1.0;
                B[i * size +j] = i + 1.0;
                C[i * size +j] = 0.0;
            }
        }
    }
}
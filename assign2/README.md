
# CPD Projects

CPD Projects of group T14G14.

## Team Members

1. João Lamas (up202208948@up.pt)
2. Miguel Mateus (up202206944@up.pt)
3. Pedro Fernandes (up202207987@up.pt)

## Project Setup and Execution Instructions

### Prerequisites
- Java SE 21 (or more recent) installed

### 1. Docker and Ollama Setup

1. Ensure Docker is running:
   ```
   sudo systemctl status docker
   ```
   If not running, start it:
   ```
   sudo systemctl start docker
   ```

2. Pull and run Ollama container:
   ```
   sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama
   ```
   Note: The container name 'ollama14' can be changed if needed, but remember to use the same name in subsequent commands.

3. Pull the llama2 model:
   ```
   sudo docker exec -it ollama14 ollama pull llama2
   ```
   This step may take some time depending on your internet connection as it downloads the model.

4.  Test the model:
   ```
   sudo docker exec -it ollama14 ollama run llama2
   ```
   This command allows you to test if the model is working correctly.

5. Verify everything is running:
   ```
   docker ps
   ```
   You should see the ollama14 container in the list of running containers.


### Running the Server
1. Navigate to the source directory:
   ```
   cd src
   ```
2. Compile the server and client code:
   ```
   javac server/*.java client/*.java
   ```
3. Run the server:
   ```
   java server.Server
   ```
    The server will start and listen for incoming client connections.

4. Run a client instance:
   ```
   java client.Client
   ```

Multiple clients can be run simultaneously by opening different terminal windows and using the same command.

### Ollama Configuration
- Ollama runs on localhost:11434 by default
- Container name: ollama14
- AI Model: llama2

### Notes
- Ensure the server is running before launching any clients
- Each client needs to be run in a separate terminal window


package com.oss.mcpagent;

import com.oss.mcpagent.service.WolframAlphaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class TcpAgentServer {

    @Value("${agent.port}")
    private int port;

    @Value("${agent.auth.password}")
    private String authPassword;

    @Value("${tls.enabled:false}")
    private boolean tlsEnabled;

    @Value("${tls.keystore.path:}")
    private String keystorePath;

    @Value("${tls.keystore.password:}")
    private String keystorePassword;

    @Value("${tls.key.password:}")
    private String keyPassword;

    private final WolframAlphaService wolframService;
    private static final Logger logger = Logger.getLogger(TcpAgentServer.class.getName());

    public TcpAgentServer(WolframAlphaService wolframService) {
        this.wolframService = wolframService;
    }

    public void startServer() {
        new Thread(() -> {
            try {
                if (tlsEnabled) {
                    startTlsServer();
                } else {
                    logger.severe("TLS is not enabled. Set tls.enabled=true in application.properties.");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to start server", e);
            }
        }).start();
    }

    private void startTlsServer() throws Exception {
        // Load keystore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream keyStoreStream = new FileInputStream(keystorePath)) {
            keyStore.load(keyStoreStream, keystorePassword.toCharArray());
        }

        // Init key manager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, keyPassword.toCharArray());

        // Init SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port)) {
            logger.info("MCP TLS Agent started on port " + port);

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        logger.info("Client connected: " + clientAddress + " at " + LocalDateTime.now());

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            writer.write("AUTHENTICATE:\n");
            writer.flush();

            String authAttempt = reader.readLine();
            if (!authPassword.equals(authAttempt)) {
                logger.warning("Authentication failed for client: " + clientAddress);
                writer.write("AUTH_FAILED\n");
                writer.flush();
                clientSocket.close();
                return;
            }

            writer.write("AUTH_SUCCESS\n");
            writer.flush();
            logger.info("Client authenticated: " + clientAddress);

            String inputLine;
            while ((inputLine = reader.readLine()) != null) {
                logger.info("Query from " + clientAddress + ": " + inputLine);
                String result = wolframService.queryWolfram(inputLine);
                writer.write(result + "\n");
                writer.flush();
                logger.info("Response sent to " + clientAddress);
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Error with client " + clientAddress, e);
        } finally {
            try {
                clientSocket.close();
                logger.info("Client disconnected: " + clientAddress);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing client socket", e);
            }
        }
    }
}

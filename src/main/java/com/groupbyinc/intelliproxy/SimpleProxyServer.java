package com.groupbyinc.intelliproxy;

import java.io.*;
import java.net.*;

public class SimpleProxyServer {

  private final int remoteport;
  private final int localport;
  private final String host;
  private boolean running = false;

  /**
   * runs a single-threaded proxy server on
   * the specified local port. It never returns.
   */
  public SimpleProxyServer(String host, int remoteport, int localport) {

    this.localport = localport;
    this.host = host;
    this.remoteport = remoteport;
  }

  public void start() throws IOException {
    running = true;
    new Thread(new Runnable() {
      @Override
      public void run() {

        // Create a ServerSocket to listen for connections with
        ServerSocket ss = null;
        try {
          ss = new ServerSocket(localport);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }

        final byte[] request = new byte[1024];
        byte[] reply = new byte[4096];

        while (running) {
          Socket client = null, server = null;
          try {
            // Wait for a connection on the local port
            client = ss.accept();

            final InputStream streamFromClient = client.getInputStream();
            final OutputStream streamToClient = client.getOutputStream();

            // Make a connection to the real server.
            // If we cannot connect to the server, send an error to the
            // client, disconnect, and continue waiting for connections.
            try {
              server = new Socket(host, remoteport);
            } catch (IOException e) {
              PrintWriter out = new PrintWriter(streamToClient);
              out.print("Proxy server cannot connect to " + host + ":"
                  + remoteport + ":\n" + e + "\n");
              out.flush();
              client.close();
              continue;
            }

            // Get server streams.
            final InputStream streamFromServer = server.getInputStream();
            final OutputStream streamToServer = server.getOutputStream();

            // a thread to read the client's requests and pass them
            // to the server. A separate thread for asynchronous.
            Thread t = new Thread() {
              public void run() {
                int bytesRead;
                try {
                  while ((bytesRead = streamFromClient.read(request)) != -1) {
                    System.out.println((char) bytesRead);
                    streamToServer.write(request, 0, bytesRead);
                    streamToServer.flush();
                  }
                } catch (IOException e) {
                }

                // the client closed the connection to us, so close our
                // connection to the server.
                try {
                  streamToServer.close();
                } catch (IOException e) {
                }
              }
            };

            // Start the client-to-server request thread running
            t.start();

            // Read the server's responses
            // and pass them back to the client.
            int bytesRead;
            try {
              while ((bytesRead = streamFromServer.read(reply)) != -1) {
                System.out.println((char) bytesRead);
                streamToClient.write(reply, 0, bytesRead);
                streamToClient.flush();
              }
            } catch (IOException e) {
            }

            // The server closed its connection to us, so we close our
            // connection to our client.
            streamToClient.close();
          } catch (IOException e) {
            System.err.println(e);
          } finally {
            try {
              if (server != null)
                server.close();
              if (client != null)
                client.close();
            } catch (IOException e) {
            }
          }
        }
      }
    }).start();
  }

  public void shutdown() {
    running = false;
  }
}
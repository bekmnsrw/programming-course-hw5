package ru.kpfu.itis.server;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        ThreadPoolExecutor serverPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        AwesomeServer server = AwesomeServer.create(5678, serverPool);

        while (true) {
            synchronized (serverPool) {
                serverPool.execute(server);

                if (serverPool.getActiveCount() >= 2) {
                    serverPool.wait();
                }
            }
        }
    }
}

package net.swined.revolut;

import net.swined.revolut.storage.Storage;

import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

public class Main {

    public static void main(String... args) throws IOException {
        new Server(new Storage()).run(Integer.parseInt(args[0]), 100, ForkJoinPool.commonPool());
    }

}

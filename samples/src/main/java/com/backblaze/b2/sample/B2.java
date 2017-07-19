/*
 * Copyright 2017, Backblaze Inc. All Rights Reserved.
 * License https://www.backblaze.com/using_b2_code.html
 */
package com.backblaze.b2.sample;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.contentSources.B2ContentTypes;
import com.backblaze.b2.client.contentSources.B2FileContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2Bucket;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UpdateBucketRequest;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.backblaze.b2.client.webApiHttpClient.B2StorageHttpClientBuilder;
import com.backblaze.b2.util.B2ExecutorUtils;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class B2 implements AutoCloseable {
    private static final String APP_NAME = "b2_4j";
    private static final String VERSION = "0.0.1";
    private static final String USER_AGENT = APP_NAME + "/" + VERSION;

    // these just don't make sense (yet), since i'm getting credentials from the environment:
    //  "    b2 authorize_account [<accountId>] [<applicationKey>]\n" +
    //  "    b2 clear_account\n" +

    // these are more work than i want to do right now:
    //  "    b2 ls [--long] [--versions] <bucketName> [<folderName>]\n" +
    //  "    b2 sync [--delete] [--keepDays N] [--skipNewer] [--replaceNewer] \\\n" +
    //  "        [--threads N] [--noProgress] <source> <destination>\n" +


    private static final String USAGE =
            "USAGE:\n" +
                    //"    b2 cancel_all_unfinished_large_files <bucketName>\n" +
                    //"    b2 cancel_large_file <fileId>\n" +
                    "    b2 create_bucket <bucketName> [allPublic | allPrivate]\n" +
                    "    b2 delete_bucket <bucketName>\n" +
                    //"    b2 delete_file_version <fileName> <fileId>\n" +
                    //"    b2 download_file_by_id [--noProgress] <fileId> <localFileName>\n" +
                    //"    b2 download_file_by_name [--noProgress] <bucketName> <fileName> <localFileName>\n" +
                    //"    b2 get_file_info <fileId>\n" +
                    //"    b2 help [commandName]\n" +
                    //"    b2 hide_file <bucketName> <fileName>\n" +
                    "    b2 list_buckets\n" +
                    //"    b2 list_file_names <bucketName> [<startFileName>] [<maxToShow>]\n" +
                    "    b2 list_file_versions <bucketName> [<startFileName>] [<startFileId>] [<maxToShow>]\n" +
                    //"    b2 list_parts <largeFileId>\n" +
                    //"    b2 list_unfinished_large_files <bucketName>\n" +
                    //"    b2 make_url <fileId>\n" +
                    "    b2 update_bucket <bucketName> [allPublic | allPrivate]\n" +
                    "    b2 upload_file [--sha1 <sha1sum>] [--contentType <contentType>] [--info <key>=<value>]* \\\n" +
                    "        [--noProgress] [--threads N] <bucketName> <localFilePath> <b2FileName>\n" +
                    "    b2 version\n";

    // where we should write normal output to.
    private final PrintStream out;

    // our client.
    private final B2StorageClient client;

    // overridden by some command line args.
    private boolean showProgress = true;
    private int numThreads = 6;

    // this is our executor.  use getExecutor() to access it.
    // it's null until the first time getExecutor() is called.
    private ExecutorService executor;

    private B2() throws B2Exception {
        out = System.out;
        client = B2StorageHttpClientBuilder.builder(USER_AGENT).build();
    }

    private ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(numThreads, B2ExecutorUtils.createThreadFactory(APP_NAME + "-%d"));
        }
        return executor;
    }

    @Override
    public void close() {
        if (executor != null) {
            B2ExecutorUtils.shutdownAndAwaitTermination(executor, 10, 10);
            executor = null;
        }
    }

    private static void usageAndExit(String errMsg) {
        System.err.println("ERROR: " + errMsg);
        System.err.println();
        System.err.println(USAGE);
        System.exit(1);
    }

    public static void main(String[] args) throws B2Exception {
        //out.println("args = [" + String.join(",", args) + "]");

        if (args.length == 0) {
            usageAndExit("you must specify which command you want to run.");
        }

        final String command = args[0];
        final String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        try (B2 b2 = new B2()) {
            if ("create_bucket".equals(command)) {
                b2.create_bucket(remainingArgs);
            } else if ("delete_bucket".equals(command)) {
                b2.delete_bucket(remainingArgs);
            } else if ("list_buckets".equals(command)) {
                b2.list_buckets(remainingArgs);
            } else if ("list_file_versions".equals(command)) {
                b2.list_file_versions(remainingArgs);
            } else if ("update_bucket".equals(command)) {
                b2.update_bucket(remainingArgs);
            } else if ("upload_file".equals(command)) {
                b2.upload_file(remainingArgs);
            } else if ("version".equals(command)) {
                b2.version(remainingArgs);
            } else {
                usageAndExit("unsupported command '" + command + "'");
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // arg-related helpers
    //
    ////////////////////////////////////////////////////////////////////////

    private void checkArgs(boolean isOk,
                           String errMsg) {
        if (!isOk) {
            usageAndExit(errMsg);
        }
    }

    private void checkArgCount(String[] args,
                               int minCount,
                               int maxCount) {
        checkArgs(args.length >= minCount, "too few arguments");
        checkArgs(args.length <= maxCount, "too many arguments");
    }

    private void checkArgCount(String[] args,
                               int exactCount) {
        checkArgCount(args, exactCount, exactCount);
    }

    private void checkArgCountIsAtLeast(String[] args,
                                        int minCount) {
        checkArgCount(args, minCount, Integer.MAX_VALUE);
    }

    private String getArgOrDie(String[] args,
                               String arg,
                               int iArg,
                               int iLastArg) {
        if (iArg > iLastArg) {
            usageAndExit("missing argument for '" + arg + "'");
        }
        return args[iArg];
    }

    private int getIntArgOrDie(String[] args,
                               String arg,
                               int iArg,
                               int iLastArg) {
        final String asString = getArgOrDie(args, arg, iArg, iLastArg);
        try {
            return Integer.parseInt(asString);
        } catch (NumberFormatException e) {
            usageAndExit("argument for '" + arg  + "' must be an integer");
            return 666;  // we never get here.
        }
    }
    private int getPositiveIntArgOrDie(String[] args,
                                       String arg,
                                       int iArg,
                                       int iLastArg) {
        final String asString = getArgOrDie(args, arg, iArg, iLastArg);
        int value = getIntArgOrDie(args, arg, iArg, iLastArg);
        if (value <= 0) {
            usageAndExit("argument for '" + arg + "' must be a POSITIVE integer");
        }
        return value;
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // helpers for the commands
    //
    ////////////////////////////////////////////////////////////////////////

    private B2Bucket getBucketByNameOrDie(String bucketName) throws B2Exception {
        for (B2Bucket bucket : client.buckets()) {
            if (bucket.getBucketName().equals(bucketName)) {
                return bucket;
            }
        }

        usageAndExit("can't find bucket named '" + bucketName + "'");
        throw new RuntimeException("usageAndExit never returns!");
    }

    private B2UploadFileRequest makeUploadRequestFromArgs(String[] args) throws B2Exception {
        // [--sha1 <sha1sum>]
        // [--contentType <contentType>]
        // [--info <key>=<value>]*
        // [--noProgress]
        // [--threads N]
        // <bucketName> <localFilePath> <b2FileName>

        checkArgCountIsAtLeast(args, 3);
        int iLastArg = args.length - 1;

        final String b2Path = args[iLastArg];
        iLastArg--;

        final String localPath = args[iLastArg];
        iLastArg--;

        final String bucketName = args[iLastArg];
        iLastArg--;

        String sha1 = null;
        String contentType = B2ContentTypes.B2_AUTO;
        final Map<String,String> infos = new TreeMap<>();

        for (int iArg=0; iArg <= iLastArg; iArg++) {
            final String arg = args[iArg];
            if ("--sha1".equals(arg)) {
                iArg++;
                sha1 = getArgOrDie(args, arg, iArg, iLastArg);
            } else if ("--noProgress".equals(arg)) {
                showProgress = false;
            } else if ("--contentType".equals(arg)) {
                iArg++;
                contentType = getArgOrDie(args, arg, iArg, iLastArg);
            } else if ("--threads".equals(arg)) {
                iArg++;
                numThreads = getPositiveIntArgOrDie(args, arg, iArg, iLastArg);
            } else if ("--info".equals(arg)) {
                iArg++;
                final String pair = getArgOrDie(args, arg, iArg, iLastArg);
                final String[] vParts = pair.split("=");
                if (vParts.length != 2 || vParts[0].isEmpty()) {
                    usageAndExit("bad format for argument to '" + arg + "' (" + pair + ")");
                }
                infos.put(vParts[0], vParts[1]);
            } else {
                usageAndExit("unexpected argument '" + arg + "'");
            }
        }

        final B2Bucket bucket = getBucketByNameOrDie(bucketName);
        final B2ContentSource source = B2FileContentSource
                .builder(new File(localPath))
                .setSha1(sha1)
                .build();

        return B2UploadFileRequest
                .builder(bucket.getBucketId(),
                        b2Path,
                        contentType,
                        source)
                .setCustomFields(infos)
                .build();
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // methods for each command
    //
    ////////////////////////////////////////////////////////////////////////


    private void create_bucket(String[] args) throws B2Exception {
        checkArgCount(args, 2);
        client.createBucket(args[0], args[1]);
    }

    private void update_bucket(String[] args) throws B2Exception {
        checkArgCount(args, 2);
        final B2Bucket bucket = getBucketByNameOrDie(args[0]);
        final B2UpdateBucketRequest request = B2UpdateBucketRequest
                .builder(bucket)
                .setBucketType(args[1])
                .build();
        client.updateBucket(request);
    }

    private void delete_bucket(String[] args) throws B2Exception {
        checkArgCount(args, 1);
        final B2Bucket bucket = getBucketByNameOrDie(args[0]);
        client.deleteBucket(bucket.getBucketId());
    }

    private void list_buckets(String[] args) throws B2Exception {
        checkArgCount(args, 0);
        for (B2Bucket bucket : client.buckets()) {
            out.println(bucket);
        }
    }

    private void list_file_versions(String[] args) throws B2Exception {
        checkArgCount(args, 1, 4);
        final B2Bucket bucket = getBucketByNameOrDie(args[0]);
        for (B2FileVersion version : client.fileVersions(bucket.getBucketId())) {
            out.println(version);
        }
    }

    private void upload_file(String[] args) throws B2Exception {
        B2UploadFileRequest request = makeUploadRequestFromArgs(args);
        if (shouldBeSmallFile(request)) {
            client.uploadSmallFile(request);
        } else {
            client.uploadLargeFile(request, getExecutor());
        }
    }

    private boolean shouldBeSmallFile(B2UploadFileRequest request) {
        return true;
    }

    private void version(String[] args) {
        checkArgCount(args, 0);
        out.println("b2 command line tool in java, version " + VERSION);
    }

}

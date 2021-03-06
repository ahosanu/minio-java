/*
 * MinIO Java SDK for Amazon S3 Compatible Cloud Storage,
 * (C) 2015-2020 MinIO, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import com.google.common.io.BaseEncoding;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.minio.BucketExistsArgs;
import io.minio.CloseableIterator;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.DeleteBucketEncryptionArgs;
import io.minio.DeleteBucketLifeCycleArgs;
import io.minio.DeleteBucketNotificationArgs;
import io.minio.DeleteBucketPolicyArgs;
import io.minio.DeleteBucketTagsArgs;
import io.minio.DeleteDefaultRetentionArgs;
import io.minio.DeleteObjectTagsArgs;
import io.minio.Directive;
import io.minio.DisableObjectLegalHoldArgs;
import io.minio.DisableVersioningArgs;
import io.minio.DownloadObjectArgs;
import io.minio.EnableObjectLegalHoldArgs;
import io.minio.EnableVersioningArgs;
import io.minio.ErrorCode;
import io.minio.GetBucketEncryptionArgs;
import io.minio.GetBucketLifeCycleArgs;
import io.minio.GetBucketNotificationArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetBucketTagsArgs;
import io.minio.GetDefaultRetentionArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.GetObjectTagsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.IsObjectLegalHoldEnabledArgs;
import io.minio.IsVersioningEnabledArgs;
import io.minio.ListIncompleteUploadsArgs;
import io.minio.ListObjectsArgs;
import io.minio.ListenBucketNotificationArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.ObjectWriteResponse;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveIncompleteUploadArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.SelectObjectContentArgs;
import io.minio.SelectResponseStream;
import io.minio.ServerSideEncryption;
import io.minio.ServerSideEncryptionCustomerKey;
import io.minio.SetBucketEncryptionArgs;
import io.minio.SetBucketLifeCycleArgs;
import io.minio.SetBucketNotificationArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.SetBucketTagsArgs;
import io.minio.SetDefaultRetentionArgs;
import io.minio.SetObjectRetentionArgs;
import io.minio.SetObjectTagsArgs;
import io.minio.StatObjectArgs;
import io.minio.Time;
import io.minio.UploadObjectArgs;
import io.minio.Xml;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteObject;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Event;
import io.minio.messages.EventType;
import io.minio.messages.FileHeaderInfo;
import io.minio.messages.InputSerialization;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.NotificationRecords;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.OutputSerialization;
import io.minio.messages.QueueConfiguration;
import io.minio.messages.QuoteFields;
import io.minio.messages.Retention;
import io.minio.messages.RetentionDuration;
import io.minio.messages.RetentionDurationDays;
import io.minio.messages.RetentionDurationYears;
import io.minio.messages.RetentionMode;
import io.minio.messages.SseAlgorithm;
import io.minio.messages.SseConfiguration;
import io.minio.messages.SseConfigurationRule;
import io.minio.messages.Stats;
import io.minio.messages.Tags;
import io.minio.messages.Upload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

@SuppressFBWarnings(
    value = "REC",
    justification = "Allow catching super class Exception since it's tests")
public class FunctionalTest {
  private static final String OS = System.getProperty("os.name").toLowerCase(Locale.US);
  private static final String MINIO_BINARY;
  private static final String PASS = "PASS";
  private static final String FAILED = "FAIL";
  private static final String IGNORED = "NA";
  private static final int KB = 1024;
  private static final int MB = 1024 * 1024;
  private static final Random random = new Random(new SecureRandom().nextLong());
  private static final String customContentType = "application/javascript";
  private static final String nullContentType = null;
  private static String bucketName = getRandomName();
  private static boolean mintEnv = false;
  private static boolean isQuickTest = false;
  private static Path dataFile1Kb;
  private static Path dataFile6Mb;
  private static String endpoint;
  private static String accessKey;
  private static String secretKey;
  private static String region;
  private static boolean isSecureEndpoint = false;
  private static String sqsArn = null;
  private static MinioClient client = null;

  private static ServerSideEncryptionCustomerKey ssec = null;
  private static ServerSideEncryption sseS3 = ServerSideEncryption.atRest();
  private static ServerSideEncryption sseKms = null;

  static {
    String binaryName = "minio";
    if (OS.contains("windows")) {
      binaryName = "minio.exe";
    }

    MINIO_BINARY = binaryName;

    try {
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256);
      ssec = ServerSideEncryption.withCustomerKey(keyGen.generateKey());
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  /** Do no-op. */
  public static void ignore(Object... args) {}

  /** Create given sized file and returns its name. */
  public static String createFile(int size) throws IOException {
    String filename = getRandomName();

    try (OutputStream os = Files.newOutputStream(Paths.get(filename), CREATE, APPEND)) {
      int totalBytesWritten = 0;
      int bytesToWrite = 0;
      byte[] buf = new byte[1 * MB];
      while (totalBytesWritten < size) {
        random.nextBytes(buf);
        bytesToWrite = size - totalBytesWritten;
        if (bytesToWrite > buf.length) {
          bytesToWrite = buf.length;
        }

        os.write(buf, 0, bytesToWrite);
        totalBytesWritten += bytesToWrite;
      }
    }

    return filename;
  }

  /** Create 1 KB temporary file. */
  public static String createFile1Kb() throws IOException {
    if (mintEnv) {
      String filename = getRandomName();
      Files.createSymbolicLink(Paths.get(filename).toAbsolutePath(), dataFile1Kb);
      return filename;
    }

    return createFile(1 * KB);
  }

  /** Create 6 MB temporary file. */
  public static String createFile6Mb() throws IOException {
    if (mintEnv) {
      String filename = getRandomName();
      Files.createSymbolicLink(Paths.get(filename).toAbsolutePath(), dataFile6Mb);
      return filename;
    }

    return createFile(6 * MB);
  }

  /** Generate random name. */
  public static String getRandomName() {
    return "minio-java-test-" + new BigInteger(32, random).toString(32);
  }

  /** Returns byte array contains all data in given InputStream. */
  public static byte[] readAllBytes(InputStream is) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int nRead;
    byte[] data = new byte[16384];
    while ((nRead = is.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, nRead);
    }
    return buffer.toByteArray();
  }

  /** Prints a success log entry in JSON format. */
  public static void mintSuccessLog(String function, String args, long startTime) {
    if (mintEnv) {
      System.out.println(
          new MintLogger(
              function, args, System.currentTimeMillis() - startTime, PASS, null, null, null));
    }
  }

  /** Prints a failure log entry in JSON format. */
  public static void mintFailedLog(
      String function, String args, long startTime, String message, String error) {
    if (mintEnv) {
      System.out.println(
          new MintLogger(
              function,
              args,
              System.currentTimeMillis() - startTime,
              FAILED,
              null,
              message,
              error));
    }
  }

  /** Prints a ignore log entry in JSON format. */
  public static void mintIgnoredLog(String function, String args, long startTime) {
    if (mintEnv) {
      System.out.println(
          new MintLogger(
              function, args, System.currentTimeMillis() - startTime, IGNORED, null, null, null));
    }
  }

  /** Read object content of the given url. */
  public static byte[] readObject(String urlString) throws Exception {
    Request.Builder requestBuilder = new Request.Builder();
    Request request = requestBuilder.url(HttpUrl.parse(urlString)).method("GET", null).build();
    OkHttpClient transport =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    Response response = transport.newCall(request).execute();

    try {
      if (response.isSuccessful()) {
        return response.body().bytes();
      }

      String errorXml = new String(response.body().bytes(), StandardCharsets.UTF_8);
      throw new Exception(
          "failed to create object. Response: " + response + ", Response body: " + errorXml);
    } finally {
      response.close();
    }
  }

  /** Write data to given object url. */
  public static void writeObject(String urlString, byte[] dataBytes) throws Exception {
    Request.Builder requestBuilder = new Request.Builder();
    // Set header 'x-amz-acl' to 'bucket-owner-full-control', so objects created
    // anonymously, can be downloaded by bucket owner in AWS S3.
    Request request =
        requestBuilder
            .url(HttpUrl.parse(urlString))
            .method("PUT", RequestBody.create(null, dataBytes))
            .addHeader("x-amz-acl", "bucket-owner-full-control")
            .build();
    OkHttpClient transport =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    Response response = transport.newCall(request).execute();

    try {
      if (!response.isSuccessful()) {
        String errorXml = new String(response.body().bytes(), StandardCharsets.UTF_8);
        throw new Exception(
            "failed to create object. Response: " + response + ", Response body: " + errorXml);
      }
    } finally {
      response.close();
    }
  }

  public static String getSha256Sum(InputStream stream, int len) throws Exception {
    MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");

    // 16KiB buffer for optimization
    byte[] buf = new byte[16384];
    int bytesToRead = buf.length;
    int bytesRead = 0;
    int totalBytesRead = 0;
    while (totalBytesRead < len) {
      if ((len - totalBytesRead) < bytesToRead) {
        bytesToRead = len - totalBytesRead;
      }

      bytesRead = stream.read(buf, 0, bytesToRead);
      if (bytesRead < 0) {
        // reached EOF
        throw new Exception("data length mismatch. expected: " + len + ", got: " + totalBytesRead);
      }

      if (bytesRead > 0) {
        sha256Digest.update(buf, 0, bytesRead);
        totalBytesRead += bytesRead;
      }
    }

    return BaseEncoding.base16().encode(sha256Digest.digest()).toLowerCase(Locale.US);
  }

  public static void skipStream(InputStream stream, int len) throws Exception {
    // 16KiB buffer for optimization
    byte[] buf = new byte[16384];
    int bytesToRead = buf.length;
    int bytesRead = 0;
    int totalBytesRead = 0;
    while (totalBytesRead < len) {
      if ((len - totalBytesRead) < bytesToRead) {
        bytesToRead = len - totalBytesRead;
      }

      bytesRead = stream.read(buf, 0, bytesToRead);
      if (bytesRead < 0) {
        // reached EOF
        throw new Exception("insufficient data. expected: " + len + ", got: " + totalBytesRead);
      }

      if (bytesRead > 0) {
        totalBytesRead += bytesRead;
      }
    }
  }

  private static void handleException(String methodName, String args, long startTime, Exception e)
      throws Exception {
    if (e instanceof ErrorResponseException) {
      if (((ErrorResponseException) e).errorResponse().errorCode() == ErrorCode.NOT_IMPLEMENTED) {
        mintIgnoredLog(methodName, args, startTime);
        return;
      }
    }

    if (mintEnv) {
      mintFailedLog(
          methodName,
          args,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
    } else {
      System.out.println("<FAILED> " + methodName + " " + ((args == null) ? "" : args));
    }

    throw e;
  }

  /** Test: makeBucket(MakeBucketArgs args). */
  public static void makeBucket_test1() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: makeBucket(MakeBucketArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog("makeBucket(MakeBucketArgs args)", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "makeBucket(MakeBucketArgs args)",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: makeBucket(MakeBucketArgs args). */
  public static void makeBucket_test2() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: with region and object lock : makeBucket(MakeBucketArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(
          MakeBucketArgs.builder().bucket(name).region("eu-west-1").objectLock(true).build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog(
          "makeBucket(MakeBucketArgs args)", "region: eu-west-1, objectLock: true", startTime);
    } catch (Exception e) {
      ErrorResponse errorResponse = null;
      if (e instanceof ErrorResponseException) {
        ErrorResponseException exp = (ErrorResponseException) e;
        errorResponse = exp.errorResponse();
      }
      // Ignore NotImplemented error
      if (errorResponse != null && errorResponse.errorCode() == ErrorCode.NOT_IMPLEMENTED) {
        mintIgnoredLog(
            "makeBucket(MakeBucketArgs args)", "region: eu-west-1, objectLock: true", startTime);
      } else {
        mintFailedLog(
            "makeBucket(MakeBucketArgs args)",
            "region: eu-west-1, objectLock: true",
            startTime,
            null,
            e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
        throw e;
      }
    }
  }

  /** Test: makeBucket(MakeBucketArgs args). */
  public static void makeBucket_test3() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: with region: makeBucket(MakeBucketArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).region("eu-west-1").build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog("makeBucket(MakeBucketArgs args) ", "region: eu-west-1", startTime);
    } catch (Exception e) {
      mintFailedLog(
          "makeBucket(MakeBucketArgs args) ",
          "region: eu-west-1",
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: makeBucket(MakeBucketArgs args) where bucketName has periods in its name. */
  public static void makeBucket_test4() throws Exception {
    if (!mintEnv) {
      System.out.println(
          "Test: with bucket name having periods in its name:  makeBucket(MakeBucketArgs args)");
    }

    long startTime = System.currentTimeMillis();
    String name = getRandomName() + ".withperiod";
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(name).region("eu-central-1").build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog(
          "makeBucket(MakeBucketArgs args) bucketname having periods in its name",
          "name: " + name + ", region: eu-central-1",
          startTime);
    } catch (Exception e) {
      mintFailedLog(
          "makeBucket(MakeBucketArgs args) bucketname having periods in its name",
          "name: " + name + ", region: eu-central-1",
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: enableVersioning(EnableVersioningArgs args). */
  public static void enableVersioning_test() throws Exception {
    String methodName = "enableVersioning(EnableVersioningArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
      client.enableVersioning(EnableVersioningArgs.builder().bucket(name).build());
      if (!client.isVersioningEnabled(IsVersioningEnabledArgs.builder().bucket(name).build())) {
        throw new Exception("[FAILED] isVersioningEnabled(): expected: true, got: false");
      }
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: disableVersioning(DisableVersioningArgs args). */
  public static void disableVersioning_test() throws Exception {
    String methodName = "disableVersioning(DisableVersioningArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
      client.disableVersioning(DisableVersioningArgs.builder().bucket(name).build());
      if (client.isVersioningEnabled(IsVersioningEnabledArgs.builder().bucket(name).build())) {
        throw new Exception("[FAILED] isVersioningEnabled(): expected: false, got: true");
      }

      client.enableVersioning(EnableVersioningArgs.builder().bucket(name).build());
      client.disableVersioning(DisableVersioningArgs.builder().bucket(name).build());
      if (client.isVersioningEnabled(IsVersioningEnabledArgs.builder().bucket(name).build())) {
        throw new Exception("[FAILED] isVersioningEnabled(): expected: false, got: true");
      }

      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: listBuckets(). */
  public static void listBuckets_test() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: listBuckets()");
    }

    long startTime = System.currentTimeMillis();
    try {
      long nowSeconds = ZonedDateTime.now().toEpochSecond();
      String bucketName = getRandomName();
      boolean found = false;
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      for (Bucket bucket : client.listBuckets()) {
        if (bucket.name().equals(bucketName)) {
          if (found) {
            throw new Exception(
                "[FAILED] duplicate entry " + bucketName + " found in list buckets");
          }

          found = true;
          // excuse 15 minutes
          if ((bucket.creationDate().toEpochSecond() - nowSeconds) > (15 * 60)) {
            throw new Exception(
                "[FAILED] bucket creation time too apart in "
                    + (bucket.creationDate().toEpochSecond() - nowSeconds)
                    + " seconds");
          }
        }
      }
      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      if (!found) {
        throw new Exception("[FAILED] created bucket not found in list buckets");
      }
      mintSuccessLog("listBuckets()", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "listBuckets()",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: bucketExists(BucketExistsArgs args). */
  public static void bucketExists_test() throws Exception {
    String methodName = "bucketExists(BucketExistsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
      if (!client.bucketExists(BucketExistsArgs.builder().bucket(name).build())) {
        throw new Exception("[FAILED] bucket does not exist");
      }
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: removeBucket(RemoveBucketArgs args). */
  public static void removeBucket_test() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: removeBucket(RemoveBucketArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String name = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(name).build());
      mintSuccessLog("removeBucket(RemoveBucketArgs args)", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "removeBucket(RemoveBucketArgs args)",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Tear down test setup. */
  public static void setup() throws Exception {
    long startTime = System.currentTimeMillis();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    } catch (Exception e) {
      handleException("makeBucket(MakeBucketArgs args)", null, startTime, e);
    }
  }

  /** Tear down test setup. */
  public static void teardown() throws Exception {
    long startTime = System.currentTimeMillis();
    try {
      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    } catch (Exception e) {
      handleException("removeBucket(RemoveBucketArgs args)", null, startTime, e);
    }
  }

  public static void testUploadObject(String testTags, String filename, String contentType)
      throws Exception {
    String methodName = "uploadObject()";
    long startTime = System.currentTimeMillis();
    try {
      try {
        UploadObjectArgs.Builder builder =
            UploadObjectArgs.builder().bucket(bucketName).object(filename).filename(filename);
        if (contentType != null) {
          builder.contentType(contentType);
        }
        client.uploadObject(builder.build());
        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        Files.delete(Paths.get(filename));
        client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(filename).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  /** Test: uploadObject() [single upload] */
  public static void uploadObject_test() throws Exception {
    String methodName = "uploadObject()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    testUploadObject("[single upload]", createFile1Kb(), null);

    if (isQuickTest) {
      return;
    }

    testUploadObject("[multi-part upload]", createFile6Mb(), null);
    testUploadObject("[custom content-type]", createFile1Kb(), customContentType);
  }

  public static void testPutObject(String testTags, PutObjectArgs args, ErrorCode errorCode)
      throws Exception {
    String methodName = "putObject()";
    long startTime = System.currentTimeMillis();
    try {
      try {
        client.putObject(args);
      } catch (ErrorResponseException e) {
        if (errorCode == null || e.errorResponse().errorCode() != errorCode) {
          throw e;
        }
      }
      client.removeObject(
          RemoveObjectArgs.builder().bucket(args.bucket()).object(args.object()).build());
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void testThreadedPutObject() throws Exception {
    String methodName = "putObject()";
    String testTags = "[threaded]";
    long startTime = System.currentTimeMillis();
    try {
      int count = 7;
      Thread[] threads = new Thread[count];

      for (int i = 0; i < count; i++) {
        threads[i] = new Thread(new PutObjectRunnable(client, bucketName, createFile6Mb()));
      }

      for (int i = 0; i < count; i++) {
        threads[i].start();
      }

      // Waiting for threads to complete.
      for (int i = 0; i < count; i++) {
        threads[i].join();
      }

      // All threads are completed.
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void putObject_test() throws Exception {
    String methodName = "putObject()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    testPutObject(
        "[single upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .contentType(customContentType)
            .build(),
        null);

    if (isQuickTest) {
      return;
    }

    testPutObject(
        "[multi-part upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(11 * MB), 11 * MB, -1)
            .contentType(customContentType)
            .build(),
        null);

    testPutObject(
        "[object name with path segments]",
        PutObjectArgs.builder().bucket(bucketName).object("path/to/" + getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .contentType(customContentType)
            .build(),
        null);

    testPutObject(
        "[zero sized object]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(0), 0, -1)
            .build(),
        null);

    testPutObject(
        "[object name ends with '/']",
        PutObjectArgs.builder().bucket(bucketName).object("path/to/" + getRandomName() + "/")
            .stream(new ContentInputStream(0), 0, -1)
            .contentType(customContentType)
            .build(),
        null);

    testPutObject(
        "[unknown stream size, single upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), -1, PutObjectArgs.MIN_MULTIPART_SIZE)
            .contentType(customContentType)
            .build(),
        null);

    testPutObject(
        "[unknown stream size, multi-part upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(11 * MB), -1, PutObjectArgs.MIN_MULTIPART_SIZE)
            .contentType(customContentType)
            .build(),
        null);

    Map<String, String> userMetadata = new HashMap<>();
    userMetadata.put("My-Project", "Project One");

    testPutObject(
        "[user metadata]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .userMetadata(userMetadata)
            .build(),
        null);

    Map<String, String> headers = new HashMap<>();

    headers.put("X-Amz-Storage-Class", "REDUCED_REDUNDANCY");
    testPutObject(
        "[storage-class=REDUCED_REDUNDANCY]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .headers(headers)
            .build(),
        null);

    headers.put("X-Amz-Storage-Class", "STANDARD");
    testPutObject(
        "[storage-class=STANDARD]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .headers(headers)
            .build(),
        null);

    headers.put("X-Amz-Storage-Class", "INVALID");
    testPutObject(
        "[storage-class=INVALID negative case]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .headers(headers)
            .build(),
        ErrorCode.INVALID_STORAGE_CLASS);

    testPutObject(
        "[SSE-S3]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .contentType(customContentType)
            .sse(sseS3)
            .build(),
        null);

    testThreadedPutObject();

    if (!isSecureEndpoint) {
      return;
    }

    testPutObject(
        "[SSE-C single upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .contentType(customContentType)
            .sse(ssec)
            .build(),
        null);

    testPutObject(
        "[SSE-C multi-part upload]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(11 * MB), 11 * MB, -1)
            .contentType(customContentType)
            .sse(ssec)
            .build(),
        null);

    if (sseKms == null) {
      mintIgnoredLog(methodName, null, System.currentTimeMillis());
      return;
    }

    testPutObject(
        "[SSE-KMS]",
        PutObjectArgs.builder().bucket(bucketName).object(getRandomName()).stream(
                new ContentInputStream(1 * KB), 1 * KB, -1)
            .contentType(customContentType)
            .sse(sseKms)
            .build(),
        null);
  }

  /** Test: statObject(StatObjectArgs args). */
  public static void statObject_test1() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: statObject(StatObjectArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      Map<String, String> headerMap = new HashMap<>();
      headerMap.put("my-custom-data", "foo");
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1), 1, -1)
              .contentType(customContentType)
              .userMetadata(headerMap)
              .build());

      ObjectStat objectStat =
          client.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());

      if (!(objectName.equals(objectStat.name())
          && (objectStat.length() == 1)
          && bucketName.equals(objectStat.bucketName())
          && objectStat.contentType().equals(customContentType))) {
        throw new Exception("[FAILED] object stat differs");
      }

      Map<String, List<String>> httpHeaders = objectStat.httpHeaders();
      if (!httpHeaders.containsKey("x-amz-meta-my-custom-data")) {
        throw new Exception("[FAILED] metadata not found in object stat");
      }
      List<String> values = httpHeaders.get("x-amz-meta-my-custom-data");
      if (values.size() != 1) {
        throw new Exception("[FAILED] too many metadata value. expected: 1, got: " + values.size());
      }
      if (!values.get(0).equals("foo")) {
        throw new Exception("[FAILED] wrong metadata value. expected: foo, got: " + values.get(0));
      }

      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog("statObject(StatObjectArgs args)", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "statObject(StatObjectArgs args)",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: with SSE-C: statObject(StatObjectArgs args). */
  public static void statObject_test2() throws Exception {
    long startTime = System.currentTimeMillis();
    if (!isSecureEndpoint) {
      mintIgnoredLog("statObject(StatObjectArgs args) using SSE_C.", null, startTime);
      return;
    }

    if (!mintEnv) {
      System.out.println("Test: with SSE-C: statObject(StatObjectArgs args)");
    }

    try {
      String objectName = getRandomName();

      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1), 1, -1)
              .sse(ssec)
              .build());

      ObjectStat objectStat =
          client.statObject(
              StatObjectArgs.builder().bucket(bucketName).object(objectName).ssec(ssec).build());

      if (!(objectName.equals(objectStat.name())
          && (objectStat.length() == 1)
          && bucketName.equals(objectStat.bucketName()))) {
        throw new Exception("[FAILED] object stat differs");
      }

      Map<String, List<String>> httpHeaders = objectStat.httpHeaders();
      if (!httpHeaders.containsKey("X-Amz-Server-Side-Encryption-Customer-Algorithm")) {
        throw new Exception("[FAILED] metadata not found in object stat");
      }
      List<String> values = httpHeaders.get("X-Amz-Server-Side-Encryption-Customer-Algorithm");
      if (values.size() != 1) {
        throw new Exception("[FAILED] too many metadata value. expected: 1, got: " + values.size());
      }
      if (!values.get(0).equals("AES256")) {
        throw new Exception(
            "[FAILED] wrong metadata value. expected: AES256, got: " + values.get(0));
      }

      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog("statObject(StatObjectArgs args) using SSE_C.", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "statObject(StatObjectArgs args) using SSE_C.",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: wtth non-existing objecth: statObject(StatObjectArgs args). */
  public static void statObject_test3() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: with non-existing object: statObject(StatObjectArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      client.statObject(
          StatObjectArgs.builder().bucket(bucketName).object(getRandomName() + "/").build());
    } catch (ErrorResponseException e) {
      if (e.errorResponse().errorCode() != ErrorCode.NO_SUCH_KEY) {
        mintFailedLog(
            "statObject(StatObjectArgs args) with non-existing object",
            null,
            startTime,
            null,
            e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
        throw e;
      }
    } catch (Exception e) {
      mintFailedLog(
          "statObject(StatObjectArgs args) with non-existing object",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    } finally {
      mintSuccessLog("statObject(StatObjectArgs args) with non-existing object", null, startTime);
    }
  }

  /** Test: with extra headers/query params: statObject(StatObjectArgs args). */
  public static void statObject_test4() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: with extra headers/query params: statObject(StatObjectArgs args)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      Map<String, String> headerMap = new HashMap<>();
      headerMap.put("my-custom-data", "foo");
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1), 1, -1)
              .contentType(customContentType)
              .userMetadata(headerMap)
              .build());

      HashMap<String, String> headers = new HashMap<>();
      headers.put("x-amz-request-payer", "requester");
      HashMap<String, String> queryParams = new HashMap<>();
      queryParams.put("partNumber", "1");
      ObjectStat objectStat =
          client.statObject(
              StatObjectArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .extraHeaders(headers)
                  .extraQueryParams(queryParams)
                  .build());

      if (!(objectName.equals(objectStat.name())
          && (objectStat.length() == 1)
          && bucketName.equals(objectStat.bucketName())
          && objectStat.contentType().equals(customContentType))) {
        throw new Exception("[FAILED] object stat differs");
      }

      Map<String, List<String>> httpHeaders = objectStat.httpHeaders();
      if (!httpHeaders.containsKey("x-amz-meta-my-custom-data")) {
        throw new Exception("[FAILED] metadata not found in object stat");
      }
      List<String> values = httpHeaders.get("x-amz-meta-my-custom-data");
      if (values.size() != 1) {
        throw new Exception("[FAILED] too many metadata value. expected: 1, got: " + values.size());
      }
      if (!values.get(0).equals("foo")) {
        throw new Exception("[FAILED] wrong metadata value. expected: foo, got: " + values.get(0));
      }

      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(
          "statObject(StatObjectArgs args) with extra headers/query params", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "statObject(StatObjectArgs args) with extra headers/query params",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  public static void testGetObject(
      String testTags,
      long objectSize,
      ServerSideEncryption sse,
      GetObjectArgs args,
      int length,
      String sha256sum)
      throws Exception {
    String methodName = "getObject()";
    long startTime = System.currentTimeMillis();
    try {
      PutObjectArgs.Builder builder =
          PutObjectArgs.builder().bucket(args.bucket()).object(args.object()).stream(
              new ContentInputStream(objectSize), objectSize, -1);
      if (sse != null) {
        builder.sse(sse);
      }
      client.putObject(builder.build());

      try (InputStream is = client.getObject(args)) {
        String checksum = getSha256Sum(is, length);
        if (!checksum.equals(sha256sum)) {
          throw new Exception("checksum mismatch. expected: " + sha256sum + ", got: " + checksum);
        }
      }
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    } finally {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(args.bucket()).object(args.object()).build());
    }
  }

  public static void getObject_test() throws Exception {
    String methodName = "getObject()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    testGetObject(
        "[single upload]",
        1 * KB,
        null,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).build(),
        1 * KB,
        getSha256Sum(new ContentInputStream(1 * KB), 1 * KB));

    if (isQuickTest) {
      return;
    }

    InputStream cis = new ContentInputStream(1 * KB);
    skipStream(cis, 1000);
    testGetObject(
        "[single upload, offset]",
        1 * KB,
        null,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).offset(1000L).build(),
        1 * KB - 1000,
        getSha256Sum(cis, 1 * KB - 1000));

    testGetObject(
        "[single upload, length]",
        1 * KB,
        null,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).length(256L).build(),
        256,
        getSha256Sum(new ContentInputStream(1 * KB), 256));

    cis = new ContentInputStream(1 * KB);
    skipStream(cis, 1000);
    testGetObject(
        "[single upload, offset, length]",
        1 * KB,
        null,
        GetObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .offset(1000L)
            .length(24L)
            .build(),
        24,
        getSha256Sum(cis, 24));

    cis = new ContentInputStream(1 * KB);
    skipStream(cis, 1000);
    testGetObject(
        "[single upload, offset, length beyond available]",
        1 * KB,
        null,
        GetObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .offset(1000L)
            .length(30L)
            .build(),
        24,
        getSha256Sum(cis, 24));

    testGetObject(
        "[multi-part upload]",
        6 * MB,
        null,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).build(),
        6 * MB,
        getSha256Sum(new ContentInputStream(6 * MB), 6 * MB));

    cis = new ContentInputStream(6 * MB);
    skipStream(cis, 1000);
    testGetObject(
        "[multi-part upload, offset, length]",
        6 * MB,
        null,
        GetObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .offset(1000L)
            .length(64 * 1024L)
            .build(),
        64 * KB,
        getSha256Sum(cis, 64 * 1024));

    cis = new ContentInputStream(0);
    testGetObject(
        "[zero sized object]",
        0,
        null,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).build(),
        0,
        getSha256Sum(cis, 0));

    if (!isSecureEndpoint) {
      return;
    }

    testGetObject(
        "[single upload, SSE-C]",
        1 * KB,
        ssec,
        GetObjectArgs.builder().bucket(bucketName).object(getRandomName()).ssec(ssec).build(),
        1 * KB,
        getSha256Sum(new ContentInputStream(1 * KB), 1 * KB));
  }

  public static void testDownloadObject(
      String testTags, int objectSize, ServerSideEncryption sse, DownloadObjectArgs args)
      throws Exception {
    String methodName = "downloadObject()";
    long startTime = System.currentTimeMillis();
    try {
      PutObjectArgs.Builder builder =
          PutObjectArgs.builder().bucket(args.bucket()).object(args.object()).stream(
              new ContentInputStream(objectSize), objectSize, -1);
      if (sse != null) {
        builder.sse(sse);
      }
      client.putObject(builder.build());
      client.downloadObject(args);
      Files.delete(Paths.get(args.filename()));
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    } finally {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(args.bucket()).object(args.object()).build());
    }
  }

  public static void downloadObject_test() throws Exception {
    String methodName = "downloadObject()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    String objectName = getRandomName();
    testDownloadObject(
        "[single upload]",
        1 * KB,
        null,
        DownloadObjectArgs.builder()
            .bucket(bucketName)
            .object(objectName)
            .filename(objectName + ".downloaded")
            .build());

    if (isQuickTest) {
      return;
    }

    String baseName = getRandomName();
    objectName = "path/to/" + baseName;
    testDownloadObject(
        "[single upload with multiple path segments]",
        1 * KB,
        null,
        DownloadObjectArgs.builder()
            .bucket(bucketName)
            .object(objectName)
            .filename(baseName + ".downloaded")
            .build());

    if (!isSecureEndpoint) {
      return;
    }

    objectName = getRandomName();
    testDownloadObject(
        "[single upload, SSE-C]",
        1 * KB,
        ssec,
        DownloadObjectArgs.builder()
            .bucket(bucketName)
            .object(objectName)
            .ssec(ssec)
            .filename(objectName + ".downloaded")
            .build());
  }

  public static List<ObjectWriteResponse> createObjects(String bucketName, int count, int versions)
      throws Exception {
    List<ObjectWriteResponse> results = new LinkedList<>();
    for (int i = 0; i < count; i++) {
      String objectName = getRandomName();
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                      new ContentInputStream(1), 1, -1)
                  .build()));
      if (versions > 1) {
        for (int j = 0; j < versions - 1; j++) {
          results.add(
              client.putObject(
                  PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                          new ContentInputStream(1), 1, -1)
                      .build()));
        }
      }
    }

    return results;
  }

  public static void removeObjects(String bucketName, List<ObjectWriteResponse> results)
      throws Exception {
    List<DeleteObject> objects =
        results.stream()
            .map(
                result -> {
                  return new DeleteObject(result.object(), result.versionId());
                })
            .collect(Collectors.toList());
    for (Result<?> r :
        client.removeObjects(
            RemoveObjectsArgs.builder().bucket(bucketName).objects(objects).build())) {
      ignore(r.get());
    }
  }

  public static void testListObjects(
      String testTags, ListObjectsArgs args, int objCount, int versions) throws Exception {
    String methodName = "listObjects()";
    long startTime = System.currentTimeMillis();
    String bucketName = args.bucket();
    List<ObjectWriteResponse> results = null;
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        if (versions > 0) {
          client.enableVersioning(EnableVersioningArgs.builder().bucket(bucketName).build());
        }

        results = createObjects(bucketName, objCount, versions);

        int i = 0;
        for (Result<?> r : client.listObjects(args)) {
          r.get();
          i++;
        }

        if (versions > 0) {
          objCount *= versions;
        }

        if (i != objCount) {
          throw new Exception("object count; expected=" + objCount + ", got=" + i);
        }

        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        if (results != null) {
          removeObjects(bucketName, results);
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void listObjects_test() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: listObjects()");
    }

    testListObjects("[bucket]", ListObjectsArgs.builder().bucket(getRandomName()).build(), 3, 0);

    testListObjects(
        "[bucket, prefix]",
        ListObjectsArgs.builder().bucket(getRandomName()).prefix("minio").build(),
        3,
        0);

    testListObjects(
        "[bucket, prefix, recursive]",
        ListObjectsArgs.builder().bucket(getRandomName()).prefix("minio").recursive(true).build(),
        3,
        0);

    testListObjects(
        "[bucket, versions]",
        ListObjectsArgs.builder().bucket(getRandomName()).includeVersions(true).build(),
        3,
        2);

    if (isQuickTest) {
      return;
    }

    testListObjects(
        "[empty bucket]", ListObjectsArgs.builder().bucket(getRandomName()).build(), 0, 0);

    testListObjects(
        "[bucket, prefix, recursive, 1050 objects]",
        ListObjectsArgs.builder().bucket(getRandomName()).prefix("minio").recursive(true).build(),
        1050,
        0);

    testListObjects(
        "[bucket, apiVersion1]",
        ListObjectsArgs.builder().bucket(getRandomName()).useApiVersion1(true).build(),
        3,
        0);
  }

  /** Test: removeObject(String bucketName, String objectName). */
  public static void removeObject_test1() throws Exception {
    if (!mintEnv) {
      System.out.println("Test: removeObject(String bucketName, String objectName)");
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1), 1, -1)
              .build());

      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog("removeObject(String bucketName, String objectName)", null, startTime);
    } catch (Exception e) {
      mintFailedLog(
          "removeObject(String bucketName, String objectName)",
          null,
          startTime,
          null,
          e.toString() + " >>> " + Arrays.toString(e.getStackTrace()));
      throw e;
    }
  }

  /** Test: removeObjects(RemoveObjectsArgs args). */
  public static void removeObjects_test1() throws Exception {
    String methodName = "removeObjects(RemoveObjectsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String bucketName = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      List<ObjectWriteResponse> results = null;
      try {
        results = createObjects(bucketName, 3, 0);
        results.add(
            new ObjectWriteResponse(null, bucketName, null, "nonexistent-object", null, null));
        removeObjects(bucketName, results);
        mintSuccessLog(methodName, null, startTime);
      } finally {
        if (results != null) {
          removeObjects(bucketName, results);
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: listIncompleteUploads(ListIncompleteUploadsArgs args). */
  public static void listIncompleteUploads_test1() throws Exception {
    String methodName = "listIncompleteUploads(ListIncompleteUploadsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();

      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(6 * MB), 9 * MB, -1)
                .build());
      } catch (ErrorResponseException e) {
        if (e.errorResponse().errorCode() != ErrorCode.INCOMPLETE_BODY) {
          throw e;
        }
      } catch (InsufficientDataException e) {
        ignore();
      }
      int i = 0;
      for (Result<Upload> r :
          client.listIncompleteUploads(
              ListIncompleteUploadsArgs.builder().bucket(bucketName).build())) {
        ignore(i++, r.get());
        if (i == 10) {
          break;
        }
      }
      client.removeIncompleteUpload(
          RemoveIncompleteUploadArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: listIncompleteUploads(ListIncompleteUploadsArgs args). */
  public static void listIncompleteUploads_test2() throws Exception {
    String methodName = "listIncompleteUploads(ListIncompleteUploadsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + "with prefix.");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs = "prefix :minio";
    try {
      String objectName = getRandomName();
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(6 * MB), 9 * MB, -1)
                .build());
      } catch (ErrorResponseException e) {
        if (e.errorResponse().errorCode() != ErrorCode.INCOMPLETE_BODY) {
          throw e;
        }
      } catch (InsufficientDataException e) {
        ignore();
      }

      int i = 0;
      for (Result<Upload> r :
          client.listIncompleteUploads(
              ListIncompleteUploadsArgs.builder().bucket(bucketName).prefix("minio").build())) {
        ignore(i++, r.get());
        if (i == 10) {
          break;
        }
      }

      client.removeIncompleteUpload(
          RemoveIncompleteUploadArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /**
   * Test: listIncompleteUploads(final String bucketName, final String prefix, final boolean
   * recursive).
   */
  public static void listIncompleteUploads_test3() throws Exception {
    String methodName = "listIncompleteUploads(ListIncompleteUploadsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + "with prefix and recursive as true.");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs = "prefix: minio, recursive: true";
    try {
      String objectName = getRandomName();
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(6 * MB), 9 * MB, -1)
                .build());
      } catch (ErrorResponseException e) {
        if (e.errorResponse().errorCode() != ErrorCode.INCOMPLETE_BODY) {
          throw e;
        }
      } catch (InsufficientDataException e) {
        ignore();
      }

      int i = 0;
      for (Result<Upload> r :
          client.listIncompleteUploads(
              ListIncompleteUploadsArgs.builder()
                  .bucket(bucketName)
                  .prefix("minio")
                  .recursive(true)
                  .build())) {
        ignore(i++, r.get());
        if (i == 10) {
          break;
        }
      }

      client.removeIncompleteUpload(
          RemoveIncompleteUploadArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** Test: removeIncompleteUpload(String bucketName, String objectName). */
  public static void removeIncompleteUploads_test() throws Exception {
    String methodName = "removeIncompleteUpload(RemoveIncompleteUploadArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(6 * MB), 9 * MB, -1)
                .build());
      } catch (ErrorResponseException e) {
        if (e.errorResponse().errorCode() != ErrorCode.INCOMPLETE_BODY) {
          throw e;
        }
      } catch (InsufficientDataException e) {
        ignore();
      }

      int i = 0;
      for (Result<Upload> r :
          client.listIncompleteUploads(
              ListIncompleteUploadsArgs.builder().bucket(bucketName).build())) {
        ignore(i++, r.get());
        if (i == 10) {
          break;
        }
      }

      client.removeIncompleteUpload(
          RemoveIncompleteUploadArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** public String getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test1() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned get");
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1 * KB), 1 * KB, -1)
              .build());

      byte[] inBytes;
      try (final InputStream is = new ContentInputStream(1 * KB)) {
        inBytes = readAllBytes(is);
      }

      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucketName)
                  .object(objectName)
                  .build());

      byte[] outBytes = readObject(urlString);
      if (!Arrays.equals(inBytes, outBytes)) {
        throw new Exception("object content differs");
      }

      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test2() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned get with expiry");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs = "expiry: 3600 sec";
    try {
      String objectName = getRandomName();
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1 * KB), 1 * KB, -1)
              .build());

      byte[] inBytes;
      try (final InputStream is = new ContentInputStream(1 * KB)) {
        inBytes = readAllBytes(is);
      }

      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucketName)
                  .object(objectName)
                  .expiry(3600)
                  .build());
      byte[] outBytes = readObject(urlString);
      if (!Arrays.equals(inBytes, outBytes)) {
        throw new Exception("object content differs");
      }
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** public String getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test3() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned get with expiry and params");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs =
        "presigned get object expiry : 3600 sec, reqParams : response-content-type as application/json";
    try {
      String objectName = getRandomName();
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1 * KB), 1 * KB, -1)
              .build());

      byte[] inBytes;
      try (final InputStream is = new ContentInputStream(1 * KB)) {
        inBytes = readAllBytes(is);
      }

      Map<String, String> reqParams = new HashMap<>();
      reqParams.put("response-content-type", "application/json");

      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucketName)
                  .object(objectName)
                  .expiry(3600)
                  .extraQueryParams(reqParams)
                  .build());

      byte[] outBytes = readObject(urlString);
      if (!Arrays.equals(inBytes, outBytes)) {
        throw new Exception("object content differs");
      }
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** public String getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test4() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned put");
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucketName)
                  .object(objectName)
                  .build());
      byte[] data = "hello, world".getBytes(StandardCharsets.UTF_8);
      writeObject(urlString, data);
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test5() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned put with expiry");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs = "expiry: 3600 sec";
    try {
      String objectName = getRandomName();

      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucketName)
                  .object(objectName)
                  .expiry(3600)
                  .build());
      byte[] data = "hello, world".getBytes(StandardCharsets.UTF_8);
      writeObject(urlString, data);
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** Test: getPresignedObjectUrl(GetPresignedObjectUrlArgs args). */
  public static void getPresignedObjectUrl_test6() throws Exception {
    String methodName = "getPresignedObjectUrl(GetPresignedObjectUrlArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " presigned get with expiry in TimeUnit");
    }

    long startTime = System.currentTimeMillis();
    String mintArgs = "expiry: 2 TimeUnit.DAYS";
    try {
      String objectName = getRandomName();
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  new ContentInputStream(1 * KB), 1 * KB, -1)
              .build());

      byte[] inBytes;
      try (final InputStream is = new ContentInputStream(1 * KB)) {
        inBytes = readAllBytes(is);
      }

      String urlString =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucketName)
                  .object(objectName)
                  .expiry(2, TimeUnit.DAYS)
                  .build());
      byte[] outBytes = readObject(urlString);
      if (!Arrays.equals(inBytes, outBytes)) {
        throw new Exception("object content differs");
      }
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** Test: presignedPostPolicy(PostPolicy policy). */
  public static void presignedPostPolicy_test() throws Exception {
    String methodName = "presignedPostPolicy(PostPolicy policy)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String objectName = getRandomName();
      PostPolicy policy = new PostPolicy(bucketName, objectName, ZonedDateTime.now().plusDays(7));
      policy.setContentRange(1 * MB, 4 * MB);
      Map<String, String> formData = client.presignedPostPolicy(policy);

      MultipartBody.Builder multipartBuilder = new MultipartBody.Builder();
      multipartBuilder.setType(MultipartBody.FORM);
      for (Map.Entry<String, String> entry : formData.entrySet()) {
        multipartBuilder.addFormDataPart(entry.getKey(), entry.getValue());
      }
      try (final InputStream is = new ContentInputStream(1 * MB)) {
        multipartBuilder.addFormDataPart(
            "file", objectName, RequestBody.create(null, readAllBytes(is)));
      }

      Request.Builder requestBuilder = new Request.Builder();
      String urlString = client.getObjectUrl(bucketName, "x");
      // remove last two characters to get clean url string of bucket.
      urlString = urlString.substring(0, urlString.length() - 2);
      Request request = requestBuilder.url(urlString).post(multipartBuilder.build()).build();
      OkHttpClient transport =
          new OkHttpClient()
              .newBuilder()
              .connectTimeout(20, TimeUnit.SECONDS)
              .writeTimeout(20, TimeUnit.SECONDS)
              .readTimeout(20, TimeUnit.SECONDS)
              .build();
      Response response = transport.newCall(request).execute();
      if (response == null) {
        throw new Exception("no response from server");
      }

      try {
        if (!response.isSuccessful()) {
          String errorXml = new String(response.body().bytes(), StandardCharsets.UTF_8);
          throw new Exception(
              "failed to upload object. Response: " + response + ", Error: " + errorXml);
        }
      } finally {
        response.close();
      }
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  public static void testCopyObject(
      String testTags, ServerSideEncryption sse, CopyObjectArgs args, boolean negativeCase)
      throws Exception {
    String methodName = "copyObject()";
    long startTime = System.currentTimeMillis();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(args.source().bucket()).build());
      try {
        PutObjectArgs.Builder builder =
            PutObjectArgs.builder().bucket(args.source().bucket()).object(args.source().object())
                .stream(new ContentInputStream(1 * KB), 1 * KB, -1);
        if (sse != null) {
          builder.sse(sse);
        }
        client.putObject(builder.build());

        if (negativeCase) {
          try {
            client.copyObject(args);
          } catch (ErrorResponseException e) {
            if (e.errorResponse().errorCode() != ErrorCode.PRECONDITION_FAILED) {
              throw e;
            }
          }
        } else {
          client.copyObject(args);

          ServerSideEncryptionCustomerKey ssec = null;
          if (sse instanceof ServerSideEncryptionCustomerKey) {
            ssec = (ServerSideEncryptionCustomerKey) sse;
          }
          client.statObject(
              StatObjectArgs.builder()
                  .bucket(args.bucket())
                  .object(args.object())
                  .ssec(ssec)
                  .build());
        }
        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(args.source().bucket())
                .object(args.source().object())
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(args.bucket()).object(args.object()).build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(args.source().bucket()).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void testCopyObjectMatchETag() throws Exception {
    String methodName = "copyObject()";
    String testTags = "[match etag]";
    long startTime = System.currentTimeMillis();
    String srcBucketName = getRandomName();
    String srcObjectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(srcBucketName).build());
      try {
        ObjectWriteResponse result =
            client.putObject(
                PutObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).stream(
                        new ContentInputStream(1 * KB), 1 * KB, -1)
                    .build());

        client.copyObject(
            CopyObjectArgs.builder()
                .bucket(bucketName)
                .object(srcObjectName + "-copy")
                .source(
                    CopySource.builder()
                        .bucket(srcBucketName)
                        .object(srcObjectName)
                        .matchETag(result.etag())
                        .build())
                .build());

        client.statObject(
            StatObjectArgs.builder().bucket(bucketName).object(srcObjectName + "-copy").build());

        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(srcObjectName + "-copy").build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(srcBucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void testCopyObjectMetadataReplace() throws Exception {
    String methodName = "copyObject()";
    String testTags = "[metadata replace]";
    long startTime = System.currentTimeMillis();
    String srcBucketName = getRandomName();
    String srcObjectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(srcBucketName).build());
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).stream(
                    new ContentInputStream(1 * KB), 1 * KB, -1)
                .build());

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", customContentType);
        headers.put("X-Amz-Meta-My-Project", "Project One");
        client.copyObject(
            CopyObjectArgs.builder()
                .bucket(bucketName)
                .object(srcObjectName + "-copy")
                .source(CopySource.builder().bucket(srcBucketName).object(srcObjectName).build())
                .headers(headers)
                .metadataDirective(Directive.REPLACE)
                .build());

        ObjectStat stat =
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(srcObjectName + "-copy")
                    .build());
        if (!customContentType.equals(stat.contentType())) {
          throw new Exception(
              "content type differs. expected: "
                  + customContentType
                  + ", got: "
                  + stat.contentType());
        }

        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(srcObjectName + "-copy").build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(srcBucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void testCopyObjectEmptyMetadataReplace() throws Exception {
    String methodName = "copyObject()";
    String testTags = "[empty metadata replace]";
    long startTime = System.currentTimeMillis();
    String srcBucketName = getRandomName();
    String srcObjectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(srcBucketName).build());
      try {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", customContentType);
        headers.put("X-Amz-Meta-My-Project", "Project One");
        client.putObject(
            PutObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).stream(
                    new ContentInputStream(1 * KB), 1 * KB, -1)
                .headers(headers)
                .build());

        client.copyObject(
            CopyObjectArgs.builder()
                .bucket(bucketName)
                .object(srcObjectName + "-copy")
                .source(CopySource.builder().bucket(srcBucketName).object(srcObjectName).build())
                .metadataDirective(Directive.REPLACE)
                .build());

        ObjectStat stat =
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(srcObjectName + "-copy")
                    .build());
        if (stat.httpHeaders().containsKey("X-Amz-Meta-My-Project")) {
          throw new Exception("expected user metadata to be removed in new object");
        }

        mintSuccessLog(methodName, testTags, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(srcBucketName).object(srcObjectName).build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(srcObjectName + "-copy").build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(srcBucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  public static void copyObject_test() throws Exception {
    String methodName = "copyObject()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    String objectName = getRandomName();
    testCopyObject(
        "[basic check]",
        null,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .source(CopySource.builder().bucket(getRandomName()).object(objectName).build())
            .build(),
        false);

    if (isQuickTest) {
      return;
    }

    testCopyObject(
        "[negative match etag]",
        null,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .source(
                CopySource.builder()
                    .bucket(getRandomName())
                    .object(getRandomName())
                    .matchETag("invalid-etag")
                    .build())
            .build(),
        true);

    testCopyObjectMatchETag();

    testCopyObject(
        "[not match etag]",
        null,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .source(
                CopySource.builder()
                    .bucket(getRandomName())
                    .object(getRandomName())
                    .notMatchETag("not-etag-of-source-object")
                    .build())
            .build(),
        false);

    testCopyObject(
        "[modified since]",
        null,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .source(
                CopySource.builder()
                    .bucket(getRandomName())
                    .object(getRandomName())
                    .modifiedSince(ZonedDateTime.of(2015, 05, 3, 3, 10, 10, 0, Time.UTC))
                    .build())
            .build(),
        false);

    testCopyObject(
        "[negative unmodified since]",
        null,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .source(
                CopySource.builder()
                    .bucket(getRandomName())
                    .object(getRandomName())
                    .unmodifiedSince(ZonedDateTime.of(2015, 05, 3, 3, 10, 10, 0, Time.UTC))
                    .build())
            .build(),
        true);

    testCopyObjectMetadataReplace();
    testCopyObjectEmptyMetadataReplace();

    testCopyObject(
        "[SSE-S3]",
        sseS3,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .sse(sseS3)
            .source(CopySource.builder().bucket(getRandomName()).object(getRandomName()).build())
            .build(),
        false);

    if (!isSecureEndpoint) {
      mintIgnoredLog(methodName, "[SSE-C]", System.currentTimeMillis());
      mintIgnoredLog(methodName, "[SSE-KMS]", System.currentTimeMillis());
      return;
    }

    testCopyObject(
        "[SSE-C]",
        ssec,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .sse(ssec)
            .source(
                CopySource.builder()
                    .bucket(getRandomName())
                    .object(getRandomName())
                    .ssec(ssec)
                    .build())
            .build(),
        false);

    if (sseKms == null) {
      mintIgnoredLog(methodName, "[SSE-KMS]", System.currentTimeMillis());
      return;
    }

    testCopyObject(
        "[SSE-KMS]",
        sseKms,
        CopyObjectArgs.builder()
            .bucket(bucketName)
            .object(getRandomName())
            .sse(sseKms)
            .source(CopySource.builder().bucket(getRandomName()).object(getRandomName()).build())
            .build(),
        false);
  }

  /** Test: composeObject(ComposeObjectArgs args). */
  public static void composeObject_test1() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }
    long startTime = System.currentTimeMillis();
    String mintArgs = "size: 6 MB & 6 MB ";

    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      String objectName2 = getRandomName();

      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName2).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      ComposeSource s1 = ComposeSource.builder().bucket(bucketName).object(objectName1).build();
      ComposeSource s2 = ComposeSource.builder().bucket(bucketName).object(objectName2).build();
      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      listSourceObjects.add(s2);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** Test: composeObject(ComposeObjectArgs args) with offset and length. */
  public static void composeObject_test2() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " with offset and length.");
    }

    long startTime = System.currentTimeMillis();
    final long partialLength = 6291436L;
    final long offset = 10L;
    String mintArgs = String.format("offset: %d, length: %d bytes", offset, partialLength);

    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      String objectName2 = getRandomName();
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName2).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      ComposeSource s1 =
          ComposeSource.builder()
              .bucket(bucketName)
              .object(objectName1)
              .offset(10L)
              .length(6291436L)
              .build();
      ComposeSource s2 = ComposeSource.builder().bucket(bucketName).object(objectName2).build();

      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      listSourceObjects.add(s2);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  /** Test: composeObject(ComposeObjectArgs args) with one source. */
  public static void composeObject_test3() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    String testTags = "with one source";

    if (!mintEnv) {
      System.out.println("Test: " + methodName + " " + testTags);
    }
    long startTime = System.currentTimeMillis();

    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));

      ComposeSource s1 =
          ComposeSource.builder()
              .bucket(bucketName)
              .object(objectName1)
              .offset(10L)
              .length(6291436L)
              .build();

      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  /** Test: composeObject(ComposeObjectArgs args) with SSE_C and SSE_C Target. */
  public static void composeObject_test4() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    String testTags = "[with SSE_C and SSE_C Target]";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " " + testTags);
    }

    long startTime = System.currentTimeMillis();

    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      String objectName2 = getRandomName();

      // Generate a new 256 bit AES key - This key must be remembered by the client.
      byte[] key = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

      ServerSideEncryptionCustomerKey ssePut = ServerSideEncryption.withCustomerKey(secretKeySpec);

      byte[] keyTarget = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKeySpecTarget = new SecretKeySpec(keyTarget, "AES");

      ServerSideEncryption sseTarget = ServerSideEncryption.withCustomerKey(secretKeySpecTarget);

      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .sse(ssePut)
                  .build()));
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName2).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .sse(ssePut)
                  .build()));

      ComposeSource s1 =
          ComposeSource.builder().bucket(bucketName).object(objectName1).ssec(ssePut).build();
      ComposeSource s2 =
          ComposeSource.builder().bucket(bucketName).object(objectName2).ssec(ssePut).build();

      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      listSourceObjects.add(s2);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .sse(sseTarget)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  /** Test: composeObject(ComposeObjectArgs args) with SSE_C on one source object. */
  public static void composeObject_test5() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    String testTags = "[with SSE_C on one source object]";
    if (!mintEnv) {
      System.out.println("Test: " + methodName + " " + testTags);
    }

    long startTime = System.currentTimeMillis();
    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      String objectName2 = getRandomName();

      // Generate a new 256 bit AES key - This key must be remembered by the client.
      byte[] key = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");

      ServerSideEncryptionCustomerKey ssePut = ServerSideEncryption.withCustomerKey(secretKeySpec);

      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .sse(ssePut)
                  .build()));
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName2).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));

      ComposeSource s1 =
          ComposeSource.builder().bucket(bucketName).object(objectName1).ssec(ssePut).build();
      ComposeSource s2 = ComposeSource.builder().bucket(bucketName).object(objectName2).build();

      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      listSourceObjects.add(s2);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, testTags, startTime);
    } catch (Exception e) {
      handleException(methodName, testTags, startTime, e);
    }
  }

  /**
   * Test: composeObject(String bucketName, String objectName, List&lt;ComposeSource&gt;
   * composeSources,Map &lt;String, String&gt; headerMap, ServerSideEncryption sseTarget).
   */
  public static void composeObject_test6() throws Exception {
    String methodName = "composeObject(ComposeObjectArgs args)";
    String testTags = "[with SSE_C on one source object]";
    if (!mintEnv) {
      System.out.println(methodName + " " + testTags);
    }

    long startTime = System.currentTimeMillis();
    try {
      List<ObjectWriteResponse> results = new LinkedList<>();
      String destinationObjectName = getRandomName();
      String objectName1 = getRandomName();
      String objectName2 = getRandomName();
      byte[] keyTarget = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKeySpecTarget = new SecretKeySpec(keyTarget, "AES");

      ServerSideEncryption sseTarget = ServerSideEncryption.withCustomerKey(secretKeySpecTarget);

      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName1).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      results.add(
          client.putObject(
              PutObjectArgs.builder().bucket(bucketName).object(objectName2).stream(
                      new ContentInputStream(6 * MB), 6 * MB, -1)
                  .contentType(customContentType)
                  .build()));
      ComposeSource s1 = ComposeSource.builder().bucket(bucketName).object(objectName1).build();
      ComposeSource s2 = ComposeSource.builder().bucket(bucketName).object(objectName2).build();

      List<ComposeSource> listSourceObjects = new ArrayList<ComposeSource>();
      listSourceObjects.add(s1);
      listSourceObjects.add(s2);
      try {
        client.composeObject(
            ComposeObjectArgs.builder()
                .bucket(bucketName)
                .object(destinationObjectName)
                .sources(listSourceObjects)
                .sse(sseTarget)
                .build());
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(destinationObjectName).build());
      } finally {
        removeObjects(bucketName, results);
      }
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  public static void enableObjectLegalHold_test() throws Exception {
    String methodName = "enableObjectLegalHold()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }
    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    ObjectWriteResponse objectInfo = null;
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());

      try {
        objectInfo =
            client.putObject(
                PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                        new ContentInputStream(1 * KB), 1 * KB, -1)
                    .build());

        client.enableObjectLegalHold(
            EnableObjectLegalHoldArgs.builder().bucket(bucketName).object(objectName).build());
        if (!client.isObjectLegalHoldEnabled(
            IsObjectLegalHoldEnabledArgs.builder().bucket(bucketName).object(objectName).build())) {
          throw new Exception("[FAILED] isObjectLegalHoldEnabled(): expected: true, got: false");
        }
        client.disableObjectLegalHold(
            DisableObjectLegalHoldArgs.builder().bucket(bucketName).object(objectName).build());
        mintSuccessLog(methodName, null, startTime);
      } finally {
        if (objectInfo != null) {
          client.removeObject(
              RemoveObjectArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .versionId(objectInfo.versionId())
                  .build());
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  public static void disableObjectLegalHold_test() throws Exception {
    String methodName = "disableObjectLegalHold()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }
    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    ObjectWriteResponse objectInfo = null;
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        objectInfo =
            client.putObject(
                PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                        new ContentInputStream(1 * KB), 1 * KB, -1)
                    .build());
        client.enableObjectLegalHold(
            EnableObjectLegalHoldArgs.builder().bucket(bucketName).object(objectName).build());
        client.disableObjectLegalHold(
            DisableObjectLegalHoldArgs.builder().bucket(bucketName).object(objectName).build());
        if (client.isObjectLegalHoldEnabled(
            IsObjectLegalHoldEnabledArgs.builder().bucket(bucketName).object(objectName).build())) {
          throw new Exception("[FAILED] isObjectLegalHoldEnabled(): expected: false, got: true");
        }
      } finally {
        if (objectInfo != null) {
          client.removeObject(
              RemoveObjectArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .versionId(objectInfo.versionId())
                  .build());
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setDefaultRetention(SetDefaultRetentionArgs args). */
  public static void setDefaultRetention_test() throws Exception {
    String methodName = "setDefaultRetention(SetDefaultRetentionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String mintArgs = "config={COMPLIANCE, 10 days}";
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        ObjectLockConfiguration config =
            new ObjectLockConfiguration(RetentionMode.COMPLIANCE, new RetentionDurationDays(10));
        client.setDefaultRetention(
            SetDefaultRetentionArgs.builder().bucket(bucketName).config(config).build());
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    }
  }

  public static void testGetDefaultRetention(
      String bucketName, RetentionMode mode, RetentionDuration duration) throws Exception {
    ObjectLockConfiguration expectedConfig = new ObjectLockConfiguration(mode, duration);
    client.setDefaultRetention(
        SetDefaultRetentionArgs.builder().bucket(bucketName).config(expectedConfig).build());
    ObjectLockConfiguration config =
        client.getDefaultRetention(GetDefaultRetentionArgs.builder().bucket(bucketName).build());

    if (config.mode() != expectedConfig.mode()) {
      throw new Exception(
          "[FAILED] mode: expected: " + expectedConfig.mode() + ", got: " + config.mode());
    }

    if (config.duration().unit() != expectedConfig.duration().unit()
        || config.duration().duration() != expectedConfig.duration().duration()) {
      throw new Exception(
          "[FAILED] duration: " + expectedConfig.duration() + ", got: " + config.duration());
    }
  }

  /** Test: getDefaultRetention(GetDefaultRetentionArgs args). */
  public static void getDefaultRetention_test() throws Exception {
    String methodName = "getDefaultRetention(GetDefaultRetentionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        testGetDefaultRetention(
            bucketName, RetentionMode.COMPLIANCE, new RetentionDurationDays(10));
        testGetDefaultRetention(
            bucketName, RetentionMode.GOVERNANCE, new RetentionDurationYears(1));
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }

      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteDefaultRetention(DeleteDefaultRetentionArgs args). */
  public static void deleteDefaultRetention_test() throws Exception {
    String methodName = "deleteDefaultRetention(DeleteDefaultRetentionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        client.deleteDefaultRetention(
            DeleteDefaultRetentionArgs.builder().bucket(bucketName).build());
        ObjectLockConfiguration config =
            new ObjectLockConfiguration(RetentionMode.COMPLIANCE, new RetentionDurationDays(10));
        client.setDefaultRetention(
            SetDefaultRetentionArgs.builder().bucket(bucketName).config(config).build());
        client.deleteDefaultRetention(
            DeleteDefaultRetentionArgs.builder().bucket(bucketName).build());
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  public static void setObjectRetention_test() throws Exception {
    String methodName = "setObjectRetention()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    ObjectWriteResponse objectInfo = null;
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        objectInfo =
            client.putObject(
                PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                        new ContentInputStream(1 * KB), 1 * KB, -1)
                    .build());

        ZonedDateTime retentionUntil = ZonedDateTime.now(Time.UTC).plusDays(1);
        Retention expectedConfig = new Retention(RetentionMode.GOVERNANCE, retentionUntil);
        client.setObjectRetention(
            SetObjectRetentionArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .config(expectedConfig)
                .build());

        Retention emptyConfig = new Retention();
        client.setObjectRetention(
            SetObjectRetentionArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .config(emptyConfig)
                .bypassGovernanceMode(true)
                .build());

      } finally {
        if (objectInfo != null) {
          client.removeObject(
              RemoveObjectArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .versionId(objectInfo.versionId())
                  .build());
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  public static void getObjectRetention_test() throws Exception {
    String methodName = "getObjectRetention()";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    ObjectWriteResponse objectInfo = null;
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).objectLock(true).build());
      try {
        objectInfo =
            client.putObject(
                PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                        new ContentInputStream(1 * KB), 1 * KB, -1)
                    .build());

        ZonedDateTime retentionUntil = ZonedDateTime.now(Time.UTC).plusDays(3);
        Retention expectedConfig = new Retention(RetentionMode.GOVERNANCE, retentionUntil);
        client.setObjectRetention(
            SetObjectRetentionArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .config(expectedConfig)
                .build());

        Retention config =
            client.getObjectRetention(
                GetObjectRetentionArgs.builder().bucket(bucketName).object(objectName).build());

        if (!(config
            .retainUntilDate()
            .withNano(0)
            .equals(expectedConfig.retainUntilDate().withNano(0)))) {
          throw new Exception(
              "[FAILED] Expected: expected duration : "
                  + expectedConfig.retainUntilDate()
                  + ", got: "
                  + config.retainUntilDate());
        }

        if (config.mode() != expectedConfig.mode()) {
          throw new Exception(
              "[FAILED] Expected: expected mode: "
                  + " expected mode :"
                  + expectedConfig.mode()
                  + ", got: "
                  + config.mode());
        }

        // Check shortening retention until period
        ZonedDateTime shortenedRetentionUntil = ZonedDateTime.now(Time.UTC).plusDays(1);
        expectedConfig = new Retention(RetentionMode.GOVERNANCE, shortenedRetentionUntil);
        client.setObjectRetention(
            SetObjectRetentionArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .config(expectedConfig)
                .bypassGovernanceMode(true)
                .build());

        config =
            client.getObjectRetention(
                GetObjectRetentionArgs.builder().bucket(bucketName).object(objectName).build());

        if (!(config
            .retainUntilDate()
            .withNano(0)
            .equals(expectedConfig.retainUntilDate().withNano(0)))) {
          throw new Exception(
              "[FAILED] Expected: expected duration : "
                  + expectedConfig.retainUntilDate()
                  + ", got: "
                  + config.retainUntilDate());
        }

        if (config.mode() != expectedConfig.mode()) {
          throw new Exception(
              " [FAILED] Expected: Expected mode :"
                  + expectedConfig.mode()
                  + ", got: "
                  + config.mode());
        }

        Retention emptyConfig = new Retention();
        client.setObjectRetention(
            SetObjectRetentionArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .config(emptyConfig)
                .bypassGovernanceMode(true)
                .build());

      } finally {
        if (objectInfo != null) {
          client.removeObject(
              RemoveObjectArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .versionId(objectInfo.versionId())
                  .build());
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getBucketPolicy(GetBucketPolicyArgs args). */
  public static void getBucketPolicy_test1() throws Exception {
    String methodName = "getBucketPolicy(GetBucketPolicyArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String policy =
          "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":[\"s3:GetObject\"],\"Effect\":\"Allow\","
              + "\"Principal\":{\"AWS\":[\"*\"]},\"Resource\":[\"arn:aws:s3:::"
              + bucketName
              + "/myobject*\"],\"Sid\":\"\"}]}";
      client.setBucketPolicy(
          SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
      client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setBucketPolicy(SetBucketPolicyArgs args). */
  public static void setBucketPolicy_test1() throws Exception {
    String methodName = "setBucketPolicy(SetBucketPolicyArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String policy =
          "{\"Statement\":[{\"Action\":\"s3:GetObject\",\"Effect\":\"Allow\",\"Principal\":"
              + "\"*\",\"Resource\":\"arn:aws:s3:::"
              + bucketName
              + "/myobject*\"}],\"Version\": \"2012-10-17\"}";
      client.setBucketPolicy(
          SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteBucketPolicy(deleteBucketPolicyArgs args). */
  public static void deleteBucketPolicy_test1() throws Exception {
    String methodName = "deleteBucketPolicy(DeleteBucketPolicyArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String policy =
          "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":[\"s3:GetObject\"],\"Effect\":\"Allow\","
              + "\"Principal\":{\"AWS\":[\"*\"]},\"Resource\":[\"arn:aws:s3:::"
              + bucketName
              + "/myobject*\"],\"Sid\":\"\"}]}";
      client.setBucketPolicy(
          SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
      client.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setBucketLifeCycle(SetBucketLifeCycleArgs args). */
  public static void setBucketLifeCycle_test1() throws Exception {
    String methodName = "setBucketLifeCycle(SetBucketLifeCycleArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      String lifeCycle =
          "<LifecycleConfiguration><Rule><ID>expire-bucket</ID><Prefix></Prefix>"
              + "<Status>Enabled</Status><Expiration><Days>365</Days></Expiration>"
              + "</Rule></LifecycleConfiguration>";
      client.setBucketLifeCycle(
          SetBucketLifeCycleArgs.builder().bucket(bucketName).config(lifeCycle).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteBucketLifeCycle(DeleteBucketLifeCycleArgs args). */
  public static void deleteBucketLifeCycle_test1() throws Exception {
    String methodName = "deleteBucketLifeCycle(DeleteBucketLifeCycleArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      client.deleteBucketLifeCycle(DeleteBucketLifeCycleArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getBucketLifeCycle(GetBucketLifeCycleArgs args). */
  public static void getBucketLifeCycle_test1() throws Exception {
    String methodName = "getBucketLifeCycle(GetBucketLifeCycleArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    try {
      client.getBucketLifeCycle(GetBucketLifeCycleArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setBucketNotification(SetBucketNotificationArgs args). */
  public static void setBucketNotification_test1() throws Exception {
    String methodName = "setBucketNotification(SetBucketNotificationArgs args)";
    long startTime = System.currentTimeMillis();
    if (sqsArn == null) {
      mintIgnoredLog(methodName, null, startTime);
      return;
    }

    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    try {
      String bucketName = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region(region).build());

      List<EventType> eventList = new LinkedList<>();
      eventList.add(EventType.OBJECT_CREATED_PUT);
      eventList.add(EventType.OBJECT_CREATED_COPY);
      QueueConfiguration queueConfig = new QueueConfiguration();
      queueConfig.setQueue(sqsArn);
      queueConfig.setEvents(eventList);
      queueConfig.setPrefixRule("images");
      queueConfig.setSuffixRule("pg");

      List<QueueConfiguration> queueConfigList = new LinkedList<>();
      queueConfigList.add(queueConfig);

      NotificationConfiguration config = new NotificationConfiguration();
      config.setQueueConfigurationList(queueConfigList);

      client.setBucketNotification(
          SetBucketNotificationArgs.builder().bucket(bucketName).config(config).build());

      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getBucketNotification(GetBucketNotificationArgs args). */
  public static void getBucketNotification_test1() throws Exception {
    String methodName = "getBucketNotification(GetBucketNotificationArgs args)";
    long startTime = System.currentTimeMillis();
    if (sqsArn == null) {
      mintIgnoredLog(methodName, null, startTime);
      return;
    }

    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    try {
      String bucketName = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region(region).build());

      List<EventType> eventList = new LinkedList<>();
      eventList.add(EventType.OBJECT_CREATED_PUT);
      QueueConfiguration queueConfig = new QueueConfiguration();
      queueConfig.setQueue(sqsArn);
      queueConfig.setEvents(eventList);

      List<QueueConfiguration> queueConfigList = new LinkedList<>();
      queueConfigList.add(queueConfig);

      NotificationConfiguration expectedConfig = new NotificationConfiguration();
      expectedConfig.setQueueConfigurationList(queueConfigList);

      client.setBucketNotification(
          SetBucketNotificationArgs.builder().bucket(bucketName).config(expectedConfig).build());

      NotificationConfiguration config =
          client.getBucketNotification(
              GetBucketNotificationArgs.builder().bucket(bucketName).build());

      if (config.queueConfigurationList().size() != 1
          || !sqsArn.equals(config.queueConfigurationList().get(0).queue())
          || config.queueConfigurationList().get(0).events().size() != 1
          || config.queueConfigurationList().get(0).events().get(0)
              != EventType.OBJECT_CREATED_PUT) {
        System.out.println(
            "FAILED. expected: " + Xml.marshal(expectedConfig) + ", got: " + Xml.marshal(config));
      }

      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteBucketNotification(DeleteBucketNotificationArgs args). */
  public static void deleteBucketNotification_test1() throws Exception {
    String methodName = "deleteBucketNotification(DeleteBucketNotificationArgs args)";
    long startTime = System.currentTimeMillis();
    if (sqsArn == null) {
      mintIgnoredLog(methodName, null, startTime);
      return;
    }

    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    try {
      String bucketName = getRandomName();
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region(region).build());

      List<EventType> eventList = new LinkedList<>();
      eventList.add(EventType.OBJECT_CREATED_PUT);
      eventList.add(EventType.OBJECT_CREATED_COPY);
      QueueConfiguration queueConfig = new QueueConfiguration();
      queueConfig.setQueue(sqsArn);
      queueConfig.setEvents(eventList);
      queueConfig.setPrefixRule("images");
      queueConfig.setSuffixRule("pg");

      List<QueueConfiguration> queueConfigList = new LinkedList<>();
      queueConfigList.add(queueConfig);

      NotificationConfiguration config = new NotificationConfiguration();
      config.setQueueConfigurationList(queueConfigList);

      client.setBucketNotification(
          SetBucketNotificationArgs.builder().bucket(bucketName).config(config).build());

      client.deleteBucketNotification(
          DeleteBucketNotificationArgs.builder().bucket(bucketName).build());

      config =
          client.getBucketNotification(
              GetBucketNotificationArgs.builder().bucket(bucketName).build());
      if (config.queueConfigurationList().size() != 0) {
        System.out.println("FAILED. expected: <empty>, got: " + Xml.marshal(config));
      }

      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      mintSuccessLog(methodName, null, startTime);
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: listenBucketNotification(ListenBucketNotificationArgs args). */
  public static void listenBucketNotification_test1() throws Exception {
    String methodName = "listenBucketNotification(ListenBucketNotificationArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String file = createFile1Kb();
    String bucketName = getRandomName();
    CloseableIterator<Result<NotificationRecords>> ci = null;
    String mintArgs =
        "prefix=prefix, suffix=suffix, events={\"s3:ObjectCreated:*\", \"s3:ObjectAccessed:*\"}";
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).region(region).build());

      String[] events = {"s3:ObjectCreated:*", "s3:ObjectAccessed:*"};
      ci =
          client.listenBucketNotification(
              ListenBucketNotificationArgs.builder()
                  .bucket(bucketName)
                  .prefix("prefix")
                  .suffix("suffix")
                  .events(events)
                  .build());

      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object("prefix-random-suffix").stream(
                  new ContentInputStream(1 * KB), 1 * KB, -1)
              .build());

      while (ci.hasNext()) {
        NotificationRecords records = ci.next().get();
        if (records.events().size() == 0) {
          continue;
        }

        boolean found = false;
        for (Event event : records.events()) {
          if (event.objectName().equals("prefix-random-suffix")) {
            found = true;
            break;
          }
        }

        if (found) {
          break;
        }
      }

      mintSuccessLog(methodName, mintArgs, startTime);
    } catch (Exception e) {
      handleException(methodName, mintArgs, startTime, e);
    } finally {
      if (ci != null) {
        ci.close();
      }

      Files.delete(Paths.get(file));
      client.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object("prefix-random-suffix").build());
      client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }
  }

  /**
   * Test: selectObjectContent(String bucketName, String objectName, String sqlExpression,
   * InputSerialization is, OutputSerialization os, boolean requestProgress, Long scanStartRange,
   * Long scanEndRange, ServerSideEncryption sse).
   */
  public static void selectObjectContent_test1() throws Exception {
    String methodName = "selectObjectContent(SelectObjectContentArgs args)";
    String sqlExpression = "select * from S3Object";
    String args = "sqlExpression: " + sqlExpression + ", requestProgress: true";

    if (!mintEnv) {
      System.out.println("Test: " + methodName + ", " + args);
    }

    long startTime = System.currentTimeMillis();
    String objectName = getRandomName();
    SelectResponseStream responseStream = null;
    try {
      String expectedResult =
          "1997,Ford,E350,\"ac, abs, moon\",3000.00\n"
              + "1999,Chevy,\"Venture \"\"Extended Edition\"\"\",,4900.00\n"
              + "1999,Chevy,\"Venture \"\"Extended Edition, Very Large\"\"\",,5000.00\n"
              + "1996,Jeep,Grand Cherokee,\"MUST SELL!\n"
              + "air, moon roof, loaded\",4799.00\n";
      byte[] data =
          ("Year,Make,Model,Description,Price\n" + expectedResult).getBytes(StandardCharsets.UTF_8);
      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      client.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  bais, data.length, -1)
              .build());

      InputSerialization is =
          new InputSerialization(null, false, null, null, FileHeaderInfo.USE, null, null, null);
      OutputSerialization os =
          new OutputSerialization(null, null, null, QuoteFields.ASNEEDED, null);

      responseStream =
          client.selectObjectContent(
              SelectObjectContentArgs.builder()
                  .bucket(bucketName)
                  .object(objectName)
                  .sqlExpression(sqlExpression)
                  .inputSerialization(is)
                  .outputSerialization(os)
                  .requestProgress(true)
                  .build());

      String result = new String(readAllBytes(responseStream), StandardCharsets.UTF_8);
      if (!result.equals(expectedResult)) {
        throw new Exception("result mismatch; expected: " + expectedResult + ", got: " + result);
      }

      Stats stats = responseStream.stats();

      if (stats == null) {
        throw new Exception("stats is null");
      }

      if (stats.bytesScanned() != 256) {
        throw new Exception(
            "stats.bytesScanned mismatch; expected: 258, got: " + stats.bytesScanned());
      }

      if (stats.bytesProcessed() != 256) {
        throw new Exception(
            "stats.bytesProcessed mismatch; expected: 258, got: " + stats.bytesProcessed());
      }

      if (stats.bytesReturned() != 222) {
        throw new Exception(
            "stats.bytesReturned mismatch; expected: 222, got: " + stats.bytesReturned());
      }

      mintSuccessLog(methodName, args, startTime);
    } catch (Exception e) {
      handleException(methodName, args, startTime, e);
    } finally {
      if (responseStream != null) {
        responseStream.close();
      }
      client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }
  }

  /** Test: setBucketEncryption(SetBucketEncryptionArgs args). */
  public static void setBucketEncryption_test() throws Exception {
    String methodName = "setBucketEncryption(SetBucketEncryptionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        List<SseConfigurationRule> ruleList = new LinkedList<>();
        ruleList.add(new SseConfigurationRule(null, SseAlgorithm.AES256));
        SseConfiguration config = new SseConfiguration(ruleList);
        client.setBucketEncryption(
            SetBucketEncryptionArgs.builder().bucket(bucketName).config(config).build());
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getBucketEncryption(GetBucketEncryptionArgs args). */
  public static void getBucketEncryption_test() throws Exception {
    String methodName = "getBucketEncryption(GetBucketEncryptionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        SseConfiguration config =
            client.getBucketEncryption(
                GetBucketEncryptionArgs.builder().bucket(bucketName).build());
        if (config.rules().size() != 0) {
          throw new Exception("expected: <empty rules>, got: " + config.rules());
        }

        List<SseConfigurationRule> ruleList = new LinkedList<>();
        ruleList.add(new SseConfigurationRule(null, SseAlgorithm.AES256));
        SseConfiguration expectedConfig = new SseConfiguration(ruleList);
        client.setBucketEncryption(
            SetBucketEncryptionArgs.builder().bucket(bucketName).config(expectedConfig).build());
        config =
            client.getBucketEncryption(
                GetBucketEncryptionArgs.builder().bucket(bucketName).build());
        if (config.rules().size() != 1) {
          throw new Exception("expected: 1, got: " + config.rules().size());
        }
        if (config.rules().get(0).sseAlgorithm() != expectedConfig.rules().get(0).sseAlgorithm()) {
          throw new Exception(
              "expected: "
                  + expectedConfig.rules().get(0).sseAlgorithm()
                  + ", got: "
                  + config.rules().get(0).sseAlgorithm());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteBucketEncryption(DeleteBucketEncryptionArgs args). */
  public static void deleteBucketEncryption_test() throws Exception {
    String methodName = "deleteBucketEncryption(DeleteBucketEncryptionArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        // Delete should succeed.
        client.deleteBucketEncryption(
            DeleteBucketEncryptionArgs.builder().bucket(bucketName).build());

        List<SseConfigurationRule> ruleList = new LinkedList<>();
        ruleList.add(new SseConfigurationRule(null, SseAlgorithm.AES256));
        SseConfiguration config = new SseConfiguration(ruleList);
        client.setBucketEncryption(
            SetBucketEncryptionArgs.builder().bucket(bucketName).config(config).build());
        client.deleteBucketEncryption(
            DeleteBucketEncryptionArgs.builder().bucket(bucketName).build());
        config =
            client.getBucketEncryption(
                GetBucketEncryptionArgs.builder().bucket(bucketName).build());
        if (config.rules().size() != 0) {
          throw new Exception("expected: <empty rules>, got: " + config.rules());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setBucketTags(SetBucketTagsArgs args). */
  public static void setBucketTags_test() throws Exception {
    String methodName = "setBucketTags(SetBucketTagsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        Map<String, String> map = new HashMap<>();
        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setBucketTags(SetBucketTagsArgs.builder().bucket(bucketName).tags(map).build());
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getBucketTags(GetBucketTagsArgs args). */
  public static void getBucketTags_test() throws Exception {
    String methodName = "getBucketTags(GetBucketTagsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        Map<String, String> map = new HashMap<>();
        Tags tags = client.getBucketTags(GetBucketTagsArgs.builder().bucket(bucketName).build());
        if (!map.equals(tags.get())) {
          throw new Exception("expected: " + map + ", got: " + tags.get());
        }

        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setBucketTags(SetBucketTagsArgs.builder().bucket(bucketName).tags(map).build());
        tags = client.getBucketTags(GetBucketTagsArgs.builder().bucket(bucketName).build());
        if (!map.equals(tags.get())) {
          throw new Exception("expected: " + map + ", got: " + tags.get());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteBucketTags(DeleteBucketTagsArgs args). */
  public static void deleteBucketTags_test() throws Exception {
    String methodName = "deleteBucketTags(DeleteBucketTagsArgs args)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        // Delete should succeed.
        client.deleteBucketTags(DeleteBucketTagsArgs.builder().bucket(bucketName).build());

        Map<String, String> map = new HashMap<>();
        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setBucketTags(SetBucketTagsArgs.builder().bucket(bucketName).tags(map).build());
        client.deleteBucketTags(DeleteBucketTagsArgs.builder().bucket(bucketName).build());
        Tags tags = client.getBucketTags(GetBucketTagsArgs.builder().bucket(bucketName).build());
        if (tags.get().size() != 0) {
          throw new Exception("expected: <empty map>" + ", got: " + tags.get());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: setObjectTags(String bucketName, Tags tags). */
  public static void setObjectTags_test() throws Exception {
    String methodName = "setObjectTags(String bucketName, String bucketName, Tags tags)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(1 * KB), 1 * KB, -1)
                .build());
        Map<String, String> map = new HashMap<>();
        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setObjectTags(
            SetObjectTagsArgs.builder().bucket(bucketName).object(objectName).tags(map).build());
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: getObjectTags(String bucketName). */
  public static void getObjectTags_test() throws Exception {
    String methodName = "getObjectTags(String bucketName, String bucketName)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(1 * KB), 1 * KB, -1)
                .build());
        Map<String, String> map = new HashMap<>();
        Tags tags =
            client.getObjectTags(
                GetObjectTagsArgs.builder().bucket(bucketName).object(objectName).build());
        if (!map.equals(tags.get())) {
          throw new Exception("expected: " + map + ", got: " + tags.get());
        }

        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setObjectTags(
            SetObjectTagsArgs.builder().bucket(bucketName).object(objectName).tags(map).build());
        tags =
            client.getObjectTags(
                GetObjectTagsArgs.builder().bucket(bucketName).object(objectName).build());
        if (!map.equals(tags.get())) {
          throw new Exception("expected: " + map + ", got: " + tags.get());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** Test: deleteObjectTags(String bucketName). */
  public static void deleteObjectTags_test() throws Exception {
    String methodName = "deleteObjectTags(String bucketName, String objectName)";
    if (!mintEnv) {
      System.out.println("Test: " + methodName);
    }

    long startTime = System.currentTimeMillis();
    String bucketName = getRandomName();
    String objectName = getRandomName();
    try {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      try {
        client.putObject(
            PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                    new ContentInputStream(1 * KB), 1 * KB, -1)
                .build());
        // Delete should succeed.
        client.deleteObjectTags(
            DeleteObjectTagsArgs.builder().bucket(bucketName).object(objectName).build());

        Map<String, String> map = new HashMap<>();
        map.put("Project", "Project One");
        map.put("User", "jsmith");
        client.setObjectTags(
            SetObjectTagsArgs.builder().bucket(bucketName).object(objectName).tags(map).build());
        client.deleteObjectTags(
            DeleteObjectTagsArgs.builder().bucket(bucketName).object(objectName).build());
        Tags tags =
            client.getObjectTags(
                GetObjectTagsArgs.builder().bucket(bucketName).object(objectName).build());
        if (tags.get().size() != 0) {
          throw new Exception("expected: <empty map>" + ", got: " + tags.get());
        }
        mintSuccessLog(methodName, null, startTime);
      } finally {
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
      }
    } catch (Exception e) {
      handleException(methodName, null, startTime, e);
    }
  }

  /** runTests: runs as much as possible of test combinations. */
  public static void runTests() throws Exception {
    makeBucket_test1();
    makeBucket_test2();
    if (endpoint.toLowerCase(Locale.US).contains("s3")) {
      makeBucket_test3();
      makeBucket_test4();
    }

    listBuckets_test();

    bucketExists_test();
    enableVersioning_test();
    disableVersioning_test();

    removeBucket_test();

    setup();

    putObject_test();
    getObject_test();
    uploadObject_test();
    downloadObject_test();

    setObjectRetention_test();
    getObjectRetention_test();

    statObject_test1();
    statObject_test2();
    statObject_test3();
    statObject_test4();

    getPresignedObjectUrl_test1();
    getPresignedObjectUrl_test2();
    getPresignedObjectUrl_test3();
    getPresignedObjectUrl_test4();
    getPresignedObjectUrl_test5();
    getPresignedObjectUrl_test6();

    listObjects_test();

    removeObject_test1();
    removeObjects_test1();

    listIncompleteUploads_test1();
    listIncompleteUploads_test2();
    listIncompleteUploads_test3();

    removeIncompleteUploads_test();

    presignedPostPolicy_test();

    copyObject_test();
    composeObject_test1();
    composeObject_test2();
    composeObject_test3();

    enableObjectLegalHold_test();
    disableObjectLegalHold_test();
    setDefaultRetention_test();
    getDefaultRetention_test();

    setObjectRetention_test();
    getObjectRetention_test();

    selectObjectContent_test1();

    setBucketEncryption_test();
    getBucketEncryption_test();
    deleteBucketEncryption_test();

    setBucketTags_test();
    getBucketTags_test();
    deleteBucketTags_test();
    setObjectTags_test();
    getObjectTags_test();
    deleteObjectTags_test();

    // SSE_C tests will only work over TLS connection
    if (isSecureEndpoint) {
      composeObject_test4();
      composeObject_test5();
      composeObject_test6();
    }

    // SSE_S3 and SSE_KMS only work with Amazon AWS endpoint.
    String requestUrl = endpoint;
    if (requestUrl.contains(".amazonaws.com")) {
      setBucketLifeCycle_test1();
      getBucketLifeCycle_test1();
      deleteBucketLifeCycle_test1();
    }

    getBucketPolicy_test1();
    setBucketPolicy_test1();
    deleteBucketPolicy_test1();

    listenBucketNotification_test1();

    teardown();

    setBucketNotification_test1();
    getBucketNotification_test1();
    deleteBucketNotification_test1();
  }

  /** runQuickTests: runs tests those completely quicker. */
  public static void runQuickTests() throws Exception {
    makeBucket_test1();
    listBuckets_test();
    bucketExists_test();
    removeBucket_test();

    setup();

    uploadObject_test();
    putObject_test();
    statObject_test1();
    getObject_test();
    downloadObject_test();
    listObjects_test();
    removeObject_test1();
    listIncompleteUploads_test1();
    removeIncompleteUploads_test();
    getPresignedObjectUrl_test1();
    getPresignedObjectUrl_test2();
    presignedPostPolicy_test();
    copyObject_test();
    getBucketPolicy_test1();
    setBucketPolicy_test1();
    deleteBucketPolicy_test1();
    selectObjectContent_test1();
    listenBucketNotification_test1();
    setBucketTags_test();
    getBucketTags_test();
    deleteBucketTags_test();
    setObjectTags_test();
    getObjectTags_test();
    deleteObjectTags_test();

    teardown();
  }

  public static boolean downloadMinio() throws IOException {
    String url = "https://dl.min.io/server/minio/release/";
    if (OS.contains("linux")) {
      url += "linux-amd64/minio";
    } else if (OS.contains("windows")) {
      url += "windows-amd64/minio.exe";
    } else if (OS.contains("mac")) {
      url += "darwin-amd64/minio";
    } else {
      System.out.println("unknown operating system " + OS);
      return false;
    }

    File file = new File(MINIO_BINARY);
    if (file.exists()) {
      return true;
    }

    System.out.println("downloading " + MINIO_BINARY + " binary");

    Request.Builder requestBuilder = new Request.Builder();
    Request request = requestBuilder.url(HttpUrl.parse(url)).method("GET", null).build();
    OkHttpClient transport =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    Response response = transport.newCall(request).execute();

    try {
      if (!response.isSuccessful()) {
        System.out.println("failed to download binary " + MINIO_BINARY);
        return false;
      }

      BufferedSink bufferedSink = Okio.buffer(Okio.sink(new File(MINIO_BINARY)));
      bufferedSink.writeAll(response.body().source());
      bufferedSink.flush();
      bufferedSink.close();
    } finally {
      response.close();
    }

    if (!OS.contains("windows")) {
      file.setExecutable(true);
    }

    return true;
  }

  public static Process runMinio() throws Exception {
    File binaryPath = new File(new File(System.getProperty("user.dir")), MINIO_BINARY);
    ProcessBuilder pb =
        new ProcessBuilder(binaryPath.getPath(), "server", ".d1", ".d2", ".d3", ".d4");

    Map<String, String> env = pb.environment();
    env.put("MINIO_ACCESS_KEY", "minio");
    env.put("MINIO_SECRET_KEY", "minio123");
    env.put("MINIO_KMS_KES_ENDPOINT", "https://play.min.io:7373");
    env.put("MINIO_KMS_KES_KEY_FILE", "play.min.io.kes.root.key");
    env.put("MINIO_KMS_KES_CERT_FILE", "play.min.io.kes.root.cert");
    env.put("MINIO_KMS_KES_KEY_NAME", "my-minio-key");
    env.put("MINIO_NOTIFY_WEBHOOK_ENABLE_miniojavatest", "on");
    env.put("MINIO_NOTIFY_WEBHOOK_ENDPOINT_miniojavatest", "http://example.org/");
    sqsArn = "arn:minio:sqs::miniojavatest:webhook";

    pb.redirectErrorStream(true);
    pb.redirectOutput(ProcessBuilder.Redirect.to(new File(MINIO_BINARY + ".log")));

    System.out.println("starting minio server");
    Process p = pb.start();
    Thread.sleep(10 * 1000); // wait for 10 seconds to do real start.
    return p;
  }

  /** main(). */
  public static void main(String[] args) throws Exception {
    String mintMode = System.getenv("MINT_MODE");
    mintEnv = (mintMode != null);
    if (mintEnv) {
      isQuickTest = !mintMode.equals("full");
      String dataDir = System.getenv("MINT_DATA_DIR");
      if (dataDir != null && !dataDir.equals("")) {
        dataFile1Kb = Paths.get(dataDir, "datafile-1-kB");
        dataFile6Mb = Paths.get(dataDir, "datafile-6-MB");
      }
    }

    Process minioProcess = null;

    String kmsKeyName = "my-minio-key";
    if (args.length != 4) {
      endpoint = "http://localhost:9000";
      accessKey = "minio";
      secretKey = "minio123";
      region = "us-east-1";

      if (!downloadMinio()) {
        System.out.println("usage: FunctionalTest <ENDPOINT> <ACCESSKEY> <SECRETKEY> <REGION>");
        System.exit(-1);
      }

      minioProcess = runMinio();
      try {
        int exitValue = minioProcess.exitValue();
        System.out.println("minio server process exited with " + exitValue);
        System.out.println("usage: FunctionalTest <ENDPOINT> <ACCESSKEY> <SECRETKEY> <REGION>");
        System.exit(-1);
      } catch (IllegalThreadStateException e) {
        ignore();
      }
    } else {
      kmsKeyName = System.getenv("MINIO_JAVA_TEST_KMS_KEY_NAME");
      if (kmsKeyName == null) {
        kmsKeyName = System.getenv("MINT_KEY_ID");
      }
      sqsArn = System.getenv("MINIO_JAVA_TEST_SQS_ARN");
      endpoint = args[0];
      accessKey = args[1];
      secretKey = args[2];
      region = args[3];
    }

    isSecureEndpoint = endpoint.toLowerCase(Locale.US).contains("https://");
    if (kmsKeyName != null) {
      Map<String, String> myContext = new HashMap<>();
      myContext.put("key1", "value1");
      sseKms = ServerSideEncryption.withManagedKeys(kmsKeyName, myContext);
    }

    int exitValue = 0;
    try {
      client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
      // Enable trace for debugging.
      // client.traceOn(System.out);

      // For mint environment, run tests based on mint mode
      if (mintEnv) {
        if (isQuickTest) {
          FunctionalTest.runQuickTests();
        } else {
          FunctionalTest.runTests();
        }
      } else {
        FunctionalTest.runTests();
        isQuickTest = true;
        // Get new bucket name to avoid minio azure gateway failure.
        bucketName = getRandomName();
        // Quick tests with passed region.
        client =
            MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
        FunctionalTest.runQuickTests();
      }
    } catch (Exception e) {
      if (!mintEnv) {
        e.printStackTrace();
      }
      exitValue = -1;
    } finally {
      if (minioProcess != null) {
        minioProcess.destroy();
      }
    }

    System.exit(exitValue);
  }
}

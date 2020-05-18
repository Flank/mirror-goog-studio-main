# Signflinger

Signflinger is a library dedicated to signing Android apks with V1, V2, V3 and V4 signing. It works as a decorator to Zipflinger ZipArchive class. Beside having to create a signing option object, it is a drop-in replacement to zipflinger.

# Why?

It is legitimate to question the existence of a signing library since there is already another library, apksign, taking care of signing apks. If all we had to support was V2, V3, and V4 there would be little need for signflinger (except removing cost of spawning apksigner).

The problem signflinger solves is FAST V1 signing. In a typical dev workflow, a file is changed, it is processed and added to an apk. This apk then needs to be signed.

If signing was implemented with a "second pass" with apksigner, we would have to parse the apk Central Directory, decompress the file that was just compressed, hash it and update the Manifest signature. Signflinger merges packaging file and signing them by retaining file in memory in order to provides them as fast as possible to the Signing Engine. The speed up is near 10x, bringing down runtime from 1XXms to 1Xms.

```
+----------------+    +------------------------------------------------------+
|                |    | APKSIG      +-----------------------------------+    |
|                |    |             |          APKSIGNER TOOL           |    |
|   Signflinger  |    |             |                                   |    |
|                |    |             +-----------------------------------+    |
|                |    |                                                      |
|                |    |             +------------------------------------+   |
|                |    |             |                                    |   |
|                +--------------------->       SIGNING ENGINE            |   |
|                |    |             |                                    |   |
|                |    |             +------------------------------------+   |
|                |    |                                                      |
|                |    |             +------------------------------------+   |
|                |    |             |                                    |   |
|                |    |             |                CORE                |   |
|                |    |             +------------------------------------+   |
+----------------+    +------------------------------------------------------+
```

# Cookbook
Manipulating a zip with zipflinger happens as follow:

```
ZipArchive archive = new ZipArchive(file);
archive.delete(A);
archive.delete(B);
archive.add(C);
archive.add(D);
archive.close();
```

Here is the same manipulation but this time to generate a signed apk:

```
SignedApkOptions.Builder builder = new SignedApkOptions.Builder()
                        .setPrivateKey(privateKey)
                        .setCertificates(certificates)
                        .setExecutor(executor);
SignedApk archive = new SignedApk(file, builder.build());
archive.delete(A);
archive.delete(B);
archive.add(C);
archive.add(D);
archive.close();
```

## Internals

Signflinger relies on apksig signing engine to generate V2 signature. When the apk is closed, signflinger provides the area of the zip corresponding to the payload, central directory and end of central directory. Apksig engine uses these areas to generate a signing block.

When the signing engine returns the signing block byte array, it is inserted between the zip payload and the zip cd/eocd.

## Performances
Signflinger features an integrated benchmarks which can be run with one command-line.
```
bazel/bazel run signflinger:benchmarks
```

Running on a Z840 against a 21MiB apk, the following stats were displayed.
```
V2 signed in  34 ms (rsa-1024)
V2 signed in  41 ms (rsa-2048)
V2 signed in  48 ms (rsa-3072)
V2 signed in  52 ms (rsa-4096)
V2 signed in 127 ms (rsa-8192)
V2 signed in 572 ms (rsa-16384)

V2 signed in  28 ms (dsa-1024)
V2 signed in  34 ms (dsa-2048)
V2 signed in  36 ms (dsa-3072)

V2 signed in  36 ms (ec-p256)
V2 signed in  37 ms (ec-p384)
V2 signed in  42 ms (ec-p521)
```

Same tests, with a 41MiB apk.
```
V2 signed in  47 ms (rsa-1024)
V2 signed in  46 ms (rsa-2048)
V2 signed in  56 ms (rsa-3072)
V2 signed in  54 ms (rsa-4096)
V2 signed in 137 ms (rsa-8192)
V2 signed in 594 ms (rsa-16384)

V2 signed in  44 ms (dsa-1024)
V2 signed in  43 ms (dsa-2048)
V2 signed in  51 ms (dsa-3072)

V2 signed in  48 ms (ec-p256)
V2 signed in  48 ms (ec-p384)
V2 signed in  49 ms (ec-p521)
```






/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apkzlib.sign.v2;

import com.android.annotations.NonNull;
import com.android.apkzlib.utils.ApkZLibPair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK Signature Scheme v2 signer.
 *
 * <p>APK Signature Scheme v2 is a whole-file signature scheme which aims to protect every single
 * bit of the APK, as opposed to the JAR Signature Scheme which protects only the names and
 * uncompressed contents of ZIP entries.
 *
 * <p>TODO: Link to APK Signature Scheme v2 documentation once it's available.
 */
public abstract class ApkSignerV2 {
    /*
     * The two main goals of APK Signature Scheme v2 are:
     * 1. Detect any unauthorized modifications to the APK. This is achieved by making the signature
     *    cover every byte of the APK being signed.
     * 2. Enable much faster signature and integrity verification. This is achieved by requiring
     *    only a minimal amount of APK parsing before the signature is verified, thus completely
     *    bypassing ZIP entry decompression and by making integrity verification parallelizable by
     *    employing a hash tree.
     *
     * The generated signature block is wrapped into an APK Signing Block and inserted into the
     * original APK immediately before the start of ZIP Central Directory. This is to ensure that
     * JAR and ZIP parsers continue to work on the signed APK. The APK Signing Block is designed for
     * extensibility. For example, a future signature scheme could insert its signatures there as
     * well. The contract of the APK Signing Block is that all contents outside of the block must be
     * protected by signatures inside the block.
     */

    private static final int CONTENT_DIGESTED_CHUNK_MAX_SIZE_BYTES = 1024 * 1024;

    private static final byte[] APK_SIGNING_BLOCK_MAGIC =
          new byte[] {
              0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20,
              0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34, 0x32,
          };
    private static final int APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;

    private ApkSignerV2() {}

    /**
     * Signer configuration.
     */
    public static final class SignerConfig {
        /**
         * Private key.
         */
        @NonNull
        public PrivateKey privateKey;

        /**
         * Certificates, with the first certificate containing the public key corresponding to
         * {@link #privateKey}.
         */
        @NonNull
        public List<X509Certificate> certificates;

        /**
         * List of signature algorithms with which to sign. At least one algorithm must be
         * provided.
         */
        @NonNull
        public List<SignatureAlgorithm> signatureAlgorithms;
    }

    /**
     * Gets the APK Signature Scheme v2 signature algorithms to be used for signing an APK using the
     * provided key.
     *
     * @param minSdkVersion minimum API Level of the platform on which the APK may be installed (see
     *        AndroidManifest.xml minSdkVersion attribute)
     *
     * @throws InvalidKeyException if the provided key is not suitable for signing APKs using
     *         APK Signature Scheme v2
     */
    public static List<SignatureAlgorithm> getSuggestedSignatureAlgorithms(
            @NonNull PublicKey signingKey, int minSdkVersion) throws InvalidKeyException {
        String keyAlgorithm = signingKey.getAlgorithm();
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            // Use RSASSA-PKCS1-v1_5 signature scheme instead of RSASSA-PSS to guarantee
            // deterministic signatures which make life easier for OTA updates (fewer files
            // changed when deterministic signature schemes are used).

            // Pick a digest which is no weaker than the key.
            int modulusLengthBits = ((RSAKey) signingKey).getModulus().bitLength();
            if (modulusLengthBits <= 3072) {
                // 3072-bit RSA is roughly 128-bit strong, meaning SHA-256 is a good fit.
                return ImmutableList.of(SignatureAlgorithm.RSA_PKCS1_V1_5_WITH_SHA256);
            } else {
                // Keys longer than 3072 bit need to be paired with a stronger digest to avoid the
                // digest being the weak link. SHA-512 is the next strongest supported digest.
                return ImmutableList.of(SignatureAlgorithm.RSA_PKCS1_V1_5_WITH_SHA512);
            }
        } else if ("DSA".equalsIgnoreCase(keyAlgorithm)) {
            // DSA is supported only with SHA-256.
            return ImmutableList.of(SignatureAlgorithm.DSA_WITH_SHA256);
        } else if ("EC".equalsIgnoreCase(keyAlgorithm)) {
            // Pick a digest which is no weaker than the key.
            int keySizeBits = ((ECKey) signingKey).getParams().getOrder().bitLength();
            if (keySizeBits <= 256) {
                // 256-bit Elliptic Curve is roughly 128-bit strong, meaning SHA-256 is a good fit.
                return ImmutableList.of(SignatureAlgorithm.ECDSA_WITH_SHA256);
            } else {
                // Keys longer than 256 bit need to be paired with a stronger digest to avoid the
                // digest being the weak link. SHA-512 is the next strongest supported digest.
                return ImmutableList.of(SignatureAlgorithm.ECDSA_WITH_SHA512);
            }
        } else {
            throw new InvalidKeyException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    /**
     * Signs the provided APK using APK Signature Scheme v2 and returns the APK Signing Block
     * containing the signature.
     *
     * <p>NOTE: To enable APK signature verifier to detect v2 signature stripping, header sections
     * of META-INF/*.SF files of APK being signed must contain the
     * {@code X-Android-APK-Signed: 2} attribute.
     *
     * @param signerConfigs signer configurations, one for each signer. At least one configuration
     *        must be provided.
     *
     * @throws InvalidKeyException if a signing key is not suitable for this signature scheme or
     *         cannot be used in general
     * @throws SignatureException if an error occurs when computing digests of generating
     *         signatures
     */
    @NonNull
    public static byte[] generateApkSigningBlock(
            @NonNull DigestSource beforeCentralDir,
            @NonNull DigestSource centralDir,
            @NonNull DigestSource eocd,
            @NonNull List<SignerConfig> signerConfigs)
                    throws InvalidKeyException, SignatureException {
        if (signerConfigs.isEmpty()) {
            throw new IllegalArgumentException(
                    "No signer configs provided. At least one is required");
        }

        // Figure out which digest(s) to use for APK contents.
        Set<ContentDigestAlgorithm> contentDigestAlgorithms = Sets.newHashSetWithExpectedSize(1);
        for (SignerConfig signerConfig : signerConfigs) {
            for (SignatureAlgorithm signatureAlgorithm : signerConfig.signatureAlgorithms) {
                contentDigestAlgorithms.add(signatureAlgorithm.getContentDigestAlgorithm());
            }
        }

        // Compute digests of APK contents.
        Map<ContentDigestAlgorithm, byte[]> contentDigests; // digest algorithm ID -> digest
        try {
            contentDigests =
                    computeContentDigests(
                            contentDigestAlgorithms,
                            new DigestSource[] {beforeCentralDir, centralDir, eocd});
        } catch (DigestException e) {
            throw new SignatureException("Failed to compute digests of APK", e);
        }

        // Sign the digests and wrap the signatures and signer info into an APK Signing Block.
        return generateApkSigningBlock(signerConfigs, contentDigests);
    }

    @NonNull
    private static Map<ContentDigestAlgorithm, byte[]> computeContentDigests(
            @NonNull Set<ContentDigestAlgorithm> digestAlgorithms,
            @NonNull DigestSource[] contents) throws DigestException {
        // For each digest algorithm the result is computed as follows:
        // 1. Each segment of contents is split into consecutive chunks of 1 MB in size.
        //    The final chunk will be shorter iff the length of segment is not a multiple of 1 MB.
        //    No chunks are produced for empty (zero length) segments.
        // 2. The digest of each chunk is computed over the concatenation of byte 0xa5, the chunk's
        //    length in bytes (uint32 little-endian) and the chunk's contents.
        // 3. The output digest is computed over the concatenation of the byte 0x5a, the number of
        //    chunks (uint32 little-endian) and the concatenation of digests of chunks of all
        //    segments in-order.

        long chunkCountLong = 0;
        for (DigestSource input : contents) {
            chunkCountLong += getChunkCount(input.size(), CONTENT_DIGESTED_CHUNK_MAX_SIZE_BYTES);
        }
        if (chunkCountLong >= Integer.MAX_VALUE) {
            throw new DigestException("Input too long: " + chunkCountLong + " chunks");
        }
        int chunkCount = (int) chunkCountLong;

        ContentDigestAlgorithm[] digestAlgorithmsArray =
                digestAlgorithms.toArray(new ContentDigestAlgorithm[digestAlgorithms.size()]);
        MessageDigest[] mds = new MessageDigest[digestAlgorithmsArray.length];
        byte[][] digestsOfChunks = new byte[digestAlgorithmsArray.length][];
        int[] digestOutputSizes = new int[digestAlgorithmsArray.length];
        for (int i = 0; i < digestAlgorithmsArray.length; i++) {
            ContentDigestAlgorithm digestAlgorithm = digestAlgorithmsArray[i];
            int digestOutputSizeBytes = digestAlgorithm.getChunkDigestOutputSizeBytes();
            digestOutputSizes[i] = digestOutputSizeBytes;
            byte[] concatenationOfChunkCountAndChunkDigests =
                    new byte[5 + chunkCount * digestOutputSizeBytes];
            concatenationOfChunkCountAndChunkDigests[0] = 0x5a;
            setUnsignedInt32LittleEndian(
                    chunkCount, concatenationOfChunkCountAndChunkDigests, 1);
            digestsOfChunks[i] = concatenationOfChunkCountAndChunkDigests;
            String jcaAlgorithmName = digestAlgorithm.getJcaMessageDigestAlgorithmName();
            try {
                mds[i] = MessageDigest.getInstance(jcaAlgorithmName);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(jcaAlgorithmName + " MessageDigest not supported", e);
            }
        }

        byte[] chunkContentPrefix = new byte[5];
        chunkContentPrefix[0] = (byte) 0xa5;
        int chunkIndex = 0;
        // Optimization opportunity: digests of chunks can be computed in parallel. However,
        // determining the number of computations to be performed in parallel is non-trivial. This
        // depends on a wide range of factors, such as data source type (e.g., in-memory or fetched
        // from file), CPU/memory/disk cache bandwidth and latency, interconnect architecture of CPU
        // cores, load on the system from other threads of execution and other processes, size of
        // input.
        // For now, we compute these digests sequentially and thus have the luxury of improving
        // performance by writing the digest of each chunk into a pre-allocated buffer at exactly
        // the right position. This avoids unnecessary allocations, copying, and enables the final
        // digest to be more efficient because it's presented with all of its input in one go.
        for (DigestSource input : contents) {
            long inputOffset = 0;
            long inputRemaining = input.size();
            while (inputRemaining > 0) {
                int chunkSize =
                        (int) Math.min(inputRemaining, CONTENT_DIGESTED_CHUNK_MAX_SIZE_BYTES);
                setUnsignedInt32LittleEndian(chunkSize, chunkContentPrefix, 1);
                for (int i = 0; i < mds.length; i++) {
                    mds[i].update(chunkContentPrefix);
                }
                try {
                    input.feedDigests(inputOffset, chunkSize, mds);
                } catch (IOException e) {
                    throw new DigestException("Failed to digest chunk #" + chunkIndex, e);
                }
                for (int i = 0; i < digestAlgorithmsArray.length; i++) {
                    MessageDigest md = mds[i];
                    byte[] concatenationOfChunkCountAndChunkDigests = digestsOfChunks[i];
                    int expectedDigestSizeBytes = digestOutputSizes[i];
                    int actualDigestSizeBytes =
                            md.digest(
                                    concatenationOfChunkCountAndChunkDigests,
                                    5 + chunkIndex * expectedDigestSizeBytes,
                                    expectedDigestSizeBytes);
                    if (actualDigestSizeBytes != expectedDigestSizeBytes) {
                        throw new RuntimeException(
                                "Unexpected output size of " + md.getAlgorithm()
                                        + " digest: " + actualDigestSizeBytes);
                    }
                }
                inputOffset += chunkSize;
                inputRemaining -= chunkSize;
                chunkIndex++;
            }
        }

        Map<ContentDigestAlgorithm, byte[]> result =
                Maps.newHashMapWithExpectedSize(digestAlgorithmsArray.length);
        for (int i = 0; i < digestAlgorithmsArray.length; i++) {
            ContentDigestAlgorithm digestAlgorithm = digestAlgorithmsArray[i];
            byte[] concatenationOfChunkCountAndChunkDigests = digestsOfChunks[i];
            MessageDigest md = mds[i];
            byte[] digest = md.digest(concatenationOfChunkCountAndChunkDigests);
            result.put(digestAlgorithm, digest);
        }
        return result;
    }

    private static final long getChunkCount(long inputSize, int chunkSize) {
        return (inputSize + chunkSize - 1) / chunkSize;
    }

    private static void setUnsignedInt32LittleEndian(int value, byte[] result, int offset) {
        result[offset] = (byte) (value & 0xff);
        result[offset + 1] = (byte) ((value >> 8) & 0xff);
        result[offset + 2] = (byte) ((value >> 16) & 0xff);
        result[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static byte[] generateApkSigningBlock(
            List<SignerConfig> signerConfigs,
            Map<ContentDigestAlgorithm, byte[]> contentDigests)
                    throws InvalidKeyException, SignatureException {
        byte[] apkSignatureSchemeV2Block =
                generateApkSignatureSchemeV2Block(signerConfigs, contentDigests);
        return generateApkSigningBlock(apkSignatureSchemeV2Block);
    }

    private static byte[] generateApkSigningBlock(byte[] apkSignatureSchemeV2Block) {
        // FORMAT:
        // uint64:  size (excluding this field)
        // repeated ID-value pairs:
        //     uint64:           size (excluding this field)
        //     uint32:           ID
        //     (size - 4) bytes: value
        // uint64:  size (same as the one above)
        // uint128: magic

        int resultSize =
                8 // size
                + 8 + 4 + apkSignatureSchemeV2Block.length // v2Block as ID-value pair
                + 8 // size
                + 16 // magic
                ;
        ByteBuffer result = ByteBuffer.allocate(resultSize);
        result.order(ByteOrder.LITTLE_ENDIAN);
        long blockSizeFieldValue = resultSize - 8;
        result.putLong(blockSizeFieldValue);

        long pairSizeFieldValue = 4 + apkSignatureSchemeV2Block.length;
        result.putLong(pairSizeFieldValue);
        result.putInt(APK_SIGNATURE_SCHEME_V2_BLOCK_ID);
        result.put(apkSignatureSchemeV2Block);

        result.putLong(blockSizeFieldValue);
        result.put(APK_SIGNING_BLOCK_MAGIC);

        return result.array();
    }

    private static byte[] generateApkSignatureSchemeV2Block(
            List<SignerConfig> signerConfigs,
            Map<ContentDigestAlgorithm, byte[]> contentDigests)
                    throws InvalidKeyException, SignatureException {
        // FORMAT:
        // * length-prefixed sequence of length-prefixed signer blocks.

        List<byte[]> signerBlocks = Lists.newArrayListWithExpectedSize(signerConfigs.size());
        int signerNumber = 0;
        for (SignerConfig signerConfig : signerConfigs) {
            signerNumber++;
            byte[] signerBlock;
            try {
                signerBlock = generateSignerBlock(signerConfig, contentDigests);
            } catch (InvalidKeyException e) {
                throw new InvalidKeyException("Signer #" + signerNumber + " failed", e);
            } catch (SignatureException e) {
                throw new SignatureException("Signer #" + signerNumber + " failed", e);
            }
            signerBlocks.add(signerBlock);
        }

        return encodeAsSequenceOfLengthPrefixedElements(
                new byte[][] {
                    encodeAsSequenceOfLengthPrefixedElements(signerBlocks),
                });
    }

    private static byte[] generateSignerBlock(
            SignerConfig signerConfig,
            Map<ContentDigestAlgorithm, byte[]> contentDigests)
                    throws InvalidKeyException, SignatureException {
        if (signerConfig.certificates.isEmpty()) {
            throw new SignatureException("No certificates configured for signer");
        }
        PublicKey publicKey = signerConfig.certificates.get(0).getPublicKey();

        byte[] encodedPublicKey = encodePublicKey(publicKey);

        V2SignatureSchemeBlock.SignedData signedData = new V2SignatureSchemeBlock.SignedData();
        try {
            signedData.certificates = encodeCertificates(signerConfig.certificates);
        } catch (CertificateEncodingException e) {
            throw new SignatureException("Failed to encode certificates", e);
        }

        List<ApkZLibPair<Integer, byte[]>> digests =
                Lists.newArrayListWithExpectedSize(signerConfig.signatureAlgorithms.size());
        for (SignatureAlgorithm signatureAlgorithm : signerConfig.signatureAlgorithms) {
            ContentDigestAlgorithm contentDigestAlgorithm =
                    signatureAlgorithm.getContentDigestAlgorithm();
            byte[] contentDigest = contentDigests.get(contentDigestAlgorithm);
            if (contentDigest == null) {
                throw new RuntimeException(
                        contentDigestAlgorithm + " content digest for " + signatureAlgorithm
                                + " not computed");
            }
            digests.add(new ApkZLibPair<>(signatureAlgorithm.getId(), contentDigest));
        }
        signedData.digests = digests;

        V2SignatureSchemeBlock.Signer signer = new V2SignatureSchemeBlock.Signer();
        // FORMAT:
        // * length-prefixed sequence of length-prefixed digests:
        //   * uint32: signature algorithm ID
        //   * length-prefixed bytes: digest of contents
        // * length-prefixed sequence of certificates:
        //   * length-prefixed bytes: X.509 certificate (ASN.1 DER encoded).
        // * length-prefixed sequence of length-prefixed additional attributes:
        //   * uint32: ID
        //   * (length - 4) bytes: value
        signer.signedData = encodeAsSequenceOfLengthPrefixedElements(new byte[][] {
            encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(signedData.digests),
            encodeAsSequenceOfLengthPrefixedElements(signedData.certificates),
            // additional attributes
            new byte[0],
        });
        signer.publicKey = encodedPublicKey;
        signer.signatures =
                Lists.newArrayListWithExpectedSize(signerConfig.signatureAlgorithms.size());
        for (SignatureAlgorithm signatureAlgorithm : signerConfig.signatureAlgorithms) {
            ApkZLibPair<String, ? extends AlgorithmParameterSpec> sigAlgAndParams =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams();
            String jcaSignatureAlgorithm = sigAlgAndParams.v1;
            AlgorithmParameterSpec jcaSignatureAlgorithmParams = sigAlgAndParams.v2;
            byte[] signatureBytes;
            try {
                Signature signature = Signature.getInstance(jcaSignatureAlgorithm);
                signature.initSign(signerConfig.privateKey);
                if (jcaSignatureAlgorithmParams != null) {
                    signature.setParameter(jcaSignatureAlgorithmParams);
                }
                signature.update(signer.signedData);
                signatureBytes = signature.sign();
            } catch (InvalidKeyException e) {
                throw new InvalidKeyException("Failed sign using " + jcaSignatureAlgorithm, e);
            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                    | SignatureException e) {
                throw new SignatureException("Failed sign using " + jcaSignatureAlgorithm, e);
            }

            try {
                Signature signature = Signature.getInstance(jcaSignatureAlgorithm);
                signature.initVerify(publicKey);
                if (jcaSignatureAlgorithmParams != null) {
                    signature.setParameter(jcaSignatureAlgorithmParams);
                }
                signature.update(signer.signedData);
                if (!signature.verify(signatureBytes)) {
                    throw new SignatureException("Signature did not verify");
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeyException("Failed to verify generated " + jcaSignatureAlgorithm
                        + " signature using public key from certificate", e);
            } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                    | SignatureException e) {
                throw new SignatureException("Failed to verify generated " + jcaSignatureAlgorithm
                        + " signature using public key from certificate", e);
            }

            signer.signatures.add(new ApkZLibPair<>(signatureAlgorithm.getId(), signatureBytes));
        }

        // FORMAT:
        // * length-prefixed signed data
        // * length-prefixed sequence of length-prefixed signatures:
        //   * uint32: signature algorithm ID
        //   * length-prefixed bytes: signature of signed data
        // * length-prefixed bytes: public key (X.509 SubjectPublicKeyInfo, ASN.1 DER encoded)
        return encodeAsSequenceOfLengthPrefixedElements(
                new byte[][] {
                    signer.signedData,
                    encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
                            signer.signatures),
                    signer.publicKey,
                });
    }

    private static final class V2SignatureSchemeBlock {
        private static final class Signer {
            public byte[] signedData;
            public List<ApkZLibPair<Integer, byte[]>> signatures;
            public byte[] publicKey;
        }

        private static final class SignedData {
            public List<ApkZLibPair<Integer, byte[]>> digests;
            public List<byte[]> certificates;
        }
    }

    private static byte[] encodePublicKey(PublicKey publicKey) throws InvalidKeyException {
        byte[] encodedPublicKey = null;
        if ("X.509".equals(publicKey.getFormat())) {
            encodedPublicKey = publicKey.getEncoded();
        }
        if (encodedPublicKey == null) {
            try {
                encodedPublicKey =
                        KeyFactory.getInstance(publicKey.getAlgorithm())
                                .getKeySpec(publicKey, X509EncodedKeySpec.class)
                                .getEncoded();
            } catch (NoSuchAlgorithmException e) {
                throw new InvalidKeyException(
                        "Failed to obtain X.509 encoded form of public key " + publicKey
                                + " of class " + publicKey.getClass().getName(),
                        e);
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(
                        "Failed to obtain X.509 encoded form of public key " + publicKey
                                + " of class " + publicKey.getClass().getName(),
                        e);
            }
        }
        if ((encodedPublicKey == null) || (encodedPublicKey.length == 0)) {
            throw new InvalidKeyException(
                    "Failed to obtain X.509 encoded form of public key " + publicKey
                            + " of class " + publicKey.getClass().getName());
        }
        return encodedPublicKey;
    }

    private static List<byte[]> encodeCertificates(List<X509Certificate> certificates)
            throws CertificateEncodingException {
        List<byte[]> result = Lists.newArrayListWithExpectedSize(certificates.size());
        for (X509Certificate certificate : certificates) {
            result.add(certificate.getEncoded());
        }
        return result;
    }

    private static byte[] encodeAsSequenceOfLengthPrefixedElements(List<byte[]> sequence) {
        return encodeAsSequenceOfLengthPrefixedElements(
                sequence.toArray(new byte[sequence.size()][]));
    }

    private static byte[] encodeAsSequenceOfLengthPrefixedElements(byte[][] sequence) {
        int payloadSize = 0;
        for (byte[] element : sequence) {
            payloadSize += 4 + element.length;
        }
        ByteBuffer result = ByteBuffer.allocate(payloadSize);
        result.order(ByteOrder.LITTLE_ENDIAN);
        for (byte[] element : sequence) {
            result.putInt(element.length);
            result.put(element);
        }
        return result.array();
      }

    private static byte[] encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(
            List<ApkZLibPair<Integer, byte[]>> sequence) {
        int resultSize = 0;
        for (ApkZLibPair<Integer, byte[]> element : sequence) {
            resultSize += 12 + element.v2.length;
        }
        ByteBuffer result = ByteBuffer.allocate(resultSize);
        result.order(ByteOrder.LITTLE_ENDIAN);
        for (ApkZLibPair<Integer, byte[]> element : sequence) {
            byte[] second = element.v2;
            result.putInt(8 + second.length);
            result.putInt(element.v1);
            result.putInt(second.length);
            result.put(second);
        }
        return result.array();
    }
}

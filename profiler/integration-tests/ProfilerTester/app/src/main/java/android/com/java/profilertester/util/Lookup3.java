package android.com.java.profilertester.util;

/** Simple hash function. http://burtleburtle.net/bob/c/lookup3.c */
public final class Lookup3 {
    private static int rotate(int x, int numBits) {
        return (x << numBits) | (x >> (32 - numBits));
    }

    private static void mix(int[] abc) {
        abc[0] -= abc[2];
        abc[0] ^= rotate(abc[2], 4);
        abc[2] += abc[1];
        abc[1] -= abc[0];
        abc[1] ^= rotate(abc[0], 6);
        abc[0] += abc[2];
        abc[2] -= abc[1];
        abc[2] ^= rotate(abc[1], 8);
        abc[1] += abc[0];
        abc[0] -= abc[2];
        abc[0] ^= rotate(abc[2], 16);
        abc[2] += abc[1];
        abc[1] -= abc[0];
        abc[1] ^= rotate(abc[0], 19);
        abc[0] += abc[2];
        abc[2] -= abc[1];
        abc[2] ^= rotate(abc[1], 4);
        abc[1] += abc[0];
    }

    private static void finalize(int[] abc) {
        abc[2] ^= abc[1];
        abc[2] -= rotate(abc[1], 14);
        abc[0] ^= abc[2];
        abc[0] -= rotate(abc[2], 11);
        abc[1] ^= abc[0];
        abc[1] -= rotate(abc[0], 25);
        abc[2] ^= abc[1];
        abc[2] -= rotate(abc[1], 16);
        abc[0] ^= abc[2];
        abc[0] -= rotate(abc[2], 4);
        abc[1] ^= abc[0];
        abc[1] -= rotate(abc[0], 14);
        abc[2] ^= abc[1];
        abc[2] -= rotate(abc[1], 24);
    }

    public static int hashwords(int[] values, int seed) {
        int[] abc = new int[3];
        for (int i = 0; i < abc.length; i++) {
            abc[i] = 0xdeadbeef + (values.length << 2) + seed;
        }

        int i = 0;
        for (; i < values.length - 3; i += 3) {
            abc[0] += values[i];
            abc[1] += values[i + 1];
            abc[2] += values[i + 2];
            mix(abc);
        }

        int remainder = values.length % 3;
        switch (remainder) {
            case 3:
                abc[2] += values[i + 2];
                // fall through
            case 2:
                abc[1] += values[i + 1];
                // fall through
            case 1:
                abc[0] += values[i];
                // fall through
            default:
                finalize(abc);
        }
        return abc[2];
    }
}

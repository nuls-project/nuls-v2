package io.nuls.base.signture;

import io.nuls.base.data.NulsHash;
import io.nuls.base.data.NulsSignData;
import io.nuls.core.crypto.ECKey;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * -Djava.library.path=/usr/local/lib
 */
public class SignatureUtilTest {

    public static void main(String[] args) {
        ECKey key = new ECKey();
        long t = 0;
        for (int j = 0; j < 20; j++) {
            long l = System.nanoTime();
            for (int i = 0; i < 5000; i++) {
                NulsHash hash = NulsHash.calcHash((i + "").getBytes());
                byte[] bytes = key.sign(hash.getBytes());
                ECKey.verify(hash.getBytes(), bytes, key.getPubKey());
            }
            long l1 = (System.nanoTime() - l) / 1000000;
            t += l1;
        }
        System.out.println(t / 20);
    }

}
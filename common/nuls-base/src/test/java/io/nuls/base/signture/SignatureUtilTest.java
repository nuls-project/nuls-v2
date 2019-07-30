package io.nuls.base.signture;

import io.nuls.base.data.NulsHash;
import io.nuls.base.data.NulsSignData;
import io.nuls.core.crypto.ECKey;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SignatureUtilTest {

    @Test
    public void name() {
        ECKey key = new ECKey();
        long l = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            NulsHash hash = NulsHash.calcHash((i + "").getBytes());
            NulsSignData signData = SignatureUtil.signDigest(hash.getBytes(), key);
            ECKey.verify(hash.getBytes(), signData.getSignBytes(), key.getPubKey());
        }
        System.out.println((System.nanoTime() - l) / 1000000);
    }
}
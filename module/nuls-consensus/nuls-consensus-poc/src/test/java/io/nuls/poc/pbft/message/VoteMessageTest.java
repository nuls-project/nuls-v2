package io.nuls.poc.pbft.message;

import io.nuls.base.data.NulsHash;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Niels
 */
public class VoteMessageTest {

    @Test
    public void test() throws Exception {
        VoteMessage msg1 = new VoteMessage();
        msg1.setHeight(100L);
        msg1.setStep((byte) 1);
        msg1.setStartTime(1532345123L);
        msg1.setRound(2);
        msg1.setBlockHash(NulsHash.calcHash(new byte[]{1, 1, 0, 1, 0, 1}));
        msg1.setSign(new byte[]{0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1});
        NulsHash hash1 = (NulsHash.calcHash(msg1.serializeForDigest()));
        byte[] bytes = msg1.serialize();


        VoteMessage msg2 = new VoteMessage();
        msg2.parse(bytes, 0);
        NulsHash hash2 = (NulsHash.calcHash(msg2.serializeForDigest()));
        assertEquals(hash1, hash2);
    }

}
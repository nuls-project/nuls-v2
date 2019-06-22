package io.nuls.poc.pbft.manager;

import io.nuls.poc.pbft.BlockVoter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Niels
 */
public class VoterManager {

    private static final Map<Integer, BlockVoter> voterMap = new HashMap<>();


    public static void putVoter(int chainId, BlockVoter voter) {
        voterMap.put(chainId, voter);
    }

    public static BlockVoter getVoter(int chainId) {
        return voterMap.get(chainId);
    }
}

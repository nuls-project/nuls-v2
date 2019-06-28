package io.nuls.poc.pbft.cache;

import io.nuls.poc.pbft.model.ProduceKey;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Niels
 */
public class BlockProuceKeyCache {

    public static final LinkedBlockingQueue<ProduceKey> PRODUCE_KEYS_QUEUE = new LinkedBlockingQueue<>();

}

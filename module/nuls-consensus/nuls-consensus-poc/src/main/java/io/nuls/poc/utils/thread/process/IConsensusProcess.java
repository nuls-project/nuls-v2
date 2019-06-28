package io.nuls.poc.utils.thread.process;

import io.nuls.poc.model.bo.Chain;

/**
 * @author Niels
 */
public interface IConsensusProcess {

    public void process(Chain chain);
}

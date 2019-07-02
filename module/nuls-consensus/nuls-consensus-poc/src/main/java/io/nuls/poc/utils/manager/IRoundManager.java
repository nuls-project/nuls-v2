package io.nuls.poc.utils.manager;

import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.bo.round.MeetingRound;

/**
 * @author Niels
 */
public interface IRoundManager {
    void addRound(Chain chain, MeetingRound meetingRound);

    void rollBackRound(Chain chain, long roundIndex);

    boolean clearRound(Chain chain, int count);

    boolean clearRound(Chain chain, long roundIndex);

    MeetingRound getRoundByIndex(Chain chain, long roundIndex);

    void checkIsNeedReset(Chain chain) throws Exception;

    MeetingRound getCurrentRound(Chain chain);

    void initRound(Chain chain) throws Exception;

    MeetingRound resetRound(Chain chain, boolean isRealTime) throws Exception;

    MeetingRound createNextRound(Chain chain, BlockHeader bestBlockHeader, long roundIndex, long startTime, MeetingRound preRound) throws Exception;

    MeetingRound getRound(Chain chain, BlockHeader header, BlockExtendsData roundData, boolean isRealTime) throws Exception;

    MeetingRound getRoundByRoundIndex(Chain chain, long roundIndex, long roundStartTime, int currentMemberIndex) throws Exception;

    BlockHeader getFirstBlockOfPreRound(Chain chain, long roundIndex);

}

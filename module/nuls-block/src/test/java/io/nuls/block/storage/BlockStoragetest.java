package io.nuls.block.storage;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.po.BlockHeaderPo;
import io.nuls.block.storage.impl.BlockStorageServiceImpl;
import io.nuls.core.rockdb.service.RocksDBService;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Niels
 */
public class BlockStoragetest {


    @Test
    public void test() {
        RocksDBService.init("/Users/niels/workspace/nuls-v2/data/block/");
        BlockStorageService service = new BlockStorageServiceImpl();
        Map<String, Integer> countMap = new HashMap<>();


        for (int i = 1; i < 461717; i++) {
            BlockHeaderPo po = service.query(2, i);
            String address = AddressTool.getStringAddressByBytes(po.getPackingAddress(2));
            Integer count = countMap.get(address);
            if (null == count) {
                count = 0;
            }
            count++;
            countMap.put(address, count);
        }
        System.out.println("=====================================");
        for (String key : countMap.keySet()) {
            System.out.println(key + " , " + countMap.get(key));
        }
        System.out.println("=====================================");
    }

}

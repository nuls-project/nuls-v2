package io.nuls.api.manager;

import io.nuls.api.cache.ApiCache;
import io.nuls.api.model.po.db.AssetInfo;
import io.nuls.api.model.po.db.ChainInfo;
import io.nuls.api.model.po.db.ContextInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    /**
     * 缓存每条链的数据
     */
    private static Map<Integer, ApiCache> apiCacheMap = new ConcurrentHashMap<>();
    /**
     * 缓存所有已注册的资产信息
     */
    private static Map<String, AssetInfo> assetInfoMap = new ConcurrentHashMap<>();

    public static void addApiCache(int chainID, ApiCache apiCache) {
        apiCacheMap.put(chainID, apiCache);
    }

    public static ApiCache getCache(int chainID) {
        return apiCacheMap.get(chainID);
    }

    public static void initCache(ChainInfo chainInfo) {
        ApiCache apiCache = new ApiCache();
        apiCache.setChainInfo(chainInfo);
        ContextInfo contextInfo = new ContextInfo();
        apiCache.setContextInfo(contextInfo);
        apiCacheMap.put(chainInfo.getChainId(), apiCache);
    }

    public static void addChainInfo(ChainInfo chainInfo) {
        apiCacheMap.get(chainInfo.getChainId()).setChainInfo(chainInfo);
    }

    public static void removeChain(int chainId) {
        apiCacheMap.remove(chainId);
    }

    public static ChainInfo getChainInfo(int chainId) {
        return apiCacheMap.get(chainId).getChainInfo();
    }

    public static Map<Integer, ApiCache> getApiCaches() {
        return apiCacheMap;
    }

    public static boolean isChainExist(int chainId) {
        ApiCache cache = apiCacheMap.get(chainId);
        return cache != null;
    }

    public static Map<String, AssetInfo> getAssetInfoMap() {
        return assetInfoMap;
    }

    public static void setAssetInfoMap(Map<String, AssetInfo> assetInfoMap) {
        CacheManager.assetInfoMap = assetInfoMap;
    }

    public static AssetInfo getRegisteredAsset(String key) {
        return assetInfoMap.get(key);
    }
}

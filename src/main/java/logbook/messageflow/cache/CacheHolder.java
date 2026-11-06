package logbook.messageflow.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import logbook.messageflow.queue.QueueName;

public class CacheHolder<K, V> {

    private static final int MAX_ENTRIES = 30;
    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<K> accessQueue = new ConcurrentLinkedQueue<>();

    private static final CacheHolder<?, ?> apiInstance = new CacheHolder<>();
    private static final CacheHolder<?, ?> imageInstance = new CacheHolder<>();
    // 画像とImageJsonは別々に処理されるため、互いのキャッシュを押し出し合わないよう分ける
    // (元実装は共用だった。参照側は ServerController で x-koukainissikai-sendtype により振り分ける)
    private static final CacheHolder<?, ?> imageJsonInstance = new CacheHolder<>();

    private CacheHolder() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> CacheHolder<K, V> getInstance(QueueName queueName) {
        if (QueueName.API == queueName) {
            return (CacheHolder<K, V>) apiInstance;
        }
        if (QueueName.IMAGEJSON == queueName) {
            return (CacheHolder<K, V>) imageJsonInstance;
        }
        return (CacheHolder<K, V>) imageInstance;
    }

    public synchronized void put(K key, V value) {
        if (this.cache.size() >= MAX_ENTRIES) {
            K oldestKey = this.accessQueue.poll();
            if (oldestKey != null) {
                this.cache.remove(oldestKey);
            }
        }
        this.accessQueue.offer(key);
        this.cache.put(key, value);
    }

    public V get(K key) {
        return this.cache.get(key);
    }

    public synchronized ConcurrentHashMap<K, V> getAll() {
        return new ConcurrentHashMap<>(this.cache);
    }
}

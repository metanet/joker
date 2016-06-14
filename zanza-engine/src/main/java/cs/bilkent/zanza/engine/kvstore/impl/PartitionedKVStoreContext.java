package cs.bilkent.zanza.engine.kvstore.impl;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import cs.bilkent.zanza.engine.kvstore.KVStoreContext;
import cs.bilkent.zanza.operator.kvstore.KVStore;
import cs.bilkent.zanza.operator.kvstore.impl.KeyDecoratedKVStore;

public class PartitionedKVStoreContext implements KVStoreContext
{

    private final String operatorId;

    private final int replicaIndex;

    private final KVStore[] kvStores;

    private final int partitionCount;

    PartitionedKVStoreContext ( final String operatorId, final int replicaIndex, final KVStore[] kvStores, final int[] partitions )
    {
        this.operatorId = operatorId;
        this.replicaIndex = replicaIndex;
        this.kvStores = Arrays.copyOf( kvStores, kvStores.length );
        this.partitionCount = partitions.length;
        for ( int partitionId = 0; partitionId < partitionCount; partitionId++ )
        {
            if ( partitions[ partitionId ] != replicaIndex )
            {
                this.kvStores[ partitionId ] = null;
            }
        }
    }

    @Override
    public String getOperatorId ()
    {
        return operatorId;
    }

    @Override
    public KVStore getKVStore ( final Object key )
    {
        int partitionId = key.hashCode() % partitionCount;
        if ( partitionId < 0 )
        {
            partitionId += partitionCount;
        }
        final KVStore kvStore = kvStores[ partitionId ];
        checkNotNull( kvStore, "partitionId=% is not in replicaIndex=%", partitionId, replicaIndex );
        return new KeyDecoratedKVStore( key, kvStore );
    }

}
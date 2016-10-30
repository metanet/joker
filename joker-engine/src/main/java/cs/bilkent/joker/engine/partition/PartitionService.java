package cs.bilkent.joker.engine.partition;

public interface PartitionService
{

    int getPartitionCount ();

    int[] getOrCreatePartitionDistribution ( int regionId, int replicaCount );

    int[] rebalancePartitionDistribution ( int regionId, int newReplicaCount );

}

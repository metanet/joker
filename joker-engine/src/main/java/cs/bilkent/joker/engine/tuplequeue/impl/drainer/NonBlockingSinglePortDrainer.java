package cs.bilkent.joker.engine.tuplequeue.impl.drainer;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import cs.bilkent.joker.engine.tuplequeue.TupleQueue;
import cs.bilkent.joker.operator.impl.TuplesImpl;
import cs.bilkent.joker.partition.impl.PartitionKey;


public class NonBlockingSinglePortDrainer extends SinglePortDrainer
{

    private final QueueLatencyRecorder latencyRecorder;

    public NonBlockingSinglePortDrainer ( final String operatorId, final int maxBatchSize )
    {
        super( operatorId, maxBatchSize );
        this.latencyRecorder = new QueueLatencyRecorder( operatorId );
    }

    @Override
    public boolean drain ( final boolean maySkipBlocking, final PartitionKey key, final TupleQueue[] queues,
                           final Function<PartitionKey, TuplesImpl> tuplesSupplier )
    {
        checkArgument( queues != null );
        checkArgument( queues.length == 1 );
        checkArgument( tuplesSupplier != null );

        final TupleQueue tupleQueue = queues[ 0 ];

        if ( tupleQueue.size() >= tupleCountToCheck )
        {
            latencyRecorder.setParameters( System.nanoTime(), tuplesSupplier.apply( key ).getTuplesModifiable( 0 ) );
            tupleQueue.drainTo( tupleCountToPoll, latencyRecorder );
            return true;
        }

        return false;
    }

}

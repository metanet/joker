package cs.bilkent.zanza.engine.pipeline.impl.downstreamtuplesender;

import java.util.concurrent.Future;

import cs.bilkent.zanza.engine.pipeline.DownstreamTupleSender;
import cs.bilkent.zanza.engine.tuplequeue.TupleQueueContext;
import cs.bilkent.zanza.operator.impl.TuplesImpl;

public class DownstreamTupleSender3 implements DownstreamTupleSender
{

    private final int sourcePortIndex1;

    private final int destinationPortIndex1;

    private final int sourcePortIndex2;

    private final int destinationPortIndex2;

    private final int sourcePortIndex3;

    private final int destinationPortIndex3;

    private final TupleQueueContext tupleQueueContext;

    public DownstreamTupleSender3 ( final int sourcePortIndex1,
                                    final int destinationPortIndex1,
                                    final int sourcePortIndex2,
                                    final int destinationPortIndex2,
                                    final int sourcePortIndex3,
                                    final int destinationPortIndex3,
                                    final TupleQueueContext tupleQueueContext )
    {
        this.sourcePortIndex1 = sourcePortIndex1;
        this.destinationPortIndex1 = destinationPortIndex1;
        this.sourcePortIndex2 = sourcePortIndex2;
        this.destinationPortIndex2 = destinationPortIndex2;
        this.sourcePortIndex3 = sourcePortIndex3;
        this.destinationPortIndex3 = destinationPortIndex3;
        this.tupleQueueContext = tupleQueueContext;
    }

    @Override
    public Future<Void> send ( final TuplesImpl tuples )
    {
        tupleQueueContext.offer( destinationPortIndex1, tuples.getTuples( sourcePortIndex1 ) );
        tupleQueueContext.offer( destinationPortIndex2, tuples.getTuples( sourcePortIndex2 ) );
        tupleQueueContext.offer( destinationPortIndex3, tuples.getTuples( sourcePortIndex3 ) );
        return null;
    }

}
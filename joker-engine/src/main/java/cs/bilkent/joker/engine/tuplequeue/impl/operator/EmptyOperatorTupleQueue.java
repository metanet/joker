package cs.bilkent.joker.engine.tuplequeue.impl.operator;

import java.util.List;
import java.util.concurrent.TimeUnit;

import cs.bilkent.joker.engine.tuplequeue.OperatorTupleQueue;
import cs.bilkent.joker.engine.tuplequeue.TupleQueue;
import cs.bilkent.joker.engine.tuplequeue.TupleQueueDrainer;
import cs.bilkent.joker.engine.tuplequeue.impl.queue.SingleThreadedTupleQueue;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.operator.scheduling.ScheduleWhenTuplesAvailable.TupleAvailabilityByPort;

public class EmptyOperatorTupleQueue implements OperatorTupleQueue
{

    private final String operatorId;

    private final TupleQueue[] tupleQueues;

    public EmptyOperatorTupleQueue ( final String operatorId, final int inputPortCount )
    {
        this.operatorId = operatorId;
        this.tupleQueues = new TupleQueue[ inputPortCount ];
        for ( int portIndex = 0; portIndex < inputPortCount; portIndex++ )
        {
            this.tupleQueues[ portIndex ] = new SingleThreadedTupleQueue( 1 );
        }
    }

    @Override
    public String getOperatorId ()
    {
        return operatorId;
    }

    @Override
    public int getInputPortCount ()
    {
        return tupleQueues.length;
    }

    @Override
    public void offer ( final int portIndex, final List<Tuple> tuples )
    {
        if ( tuples.isEmpty() )
        {
            return;
        }

        throw new UnsupportedOperationException( operatorId );
    }

    @Override
    public int tryOffer ( final int portIndex, final List<Tuple> tuples, final long timeout, final TimeUnit unit )
    {
        if ( tuples.isEmpty() )
        {
            return 0;
        }

        throw new UnsupportedOperationException( operatorId );
    }

    @Override
    public void forceOffer ( final int portIndex, final List<Tuple> tuples )
    {
        if ( tuples.isEmpty() )
        {
            return;
        }

        throw new UnsupportedOperationException( operatorId );
    }

    @Override
    public void drain ( final TupleQueueDrainer drainer )
    {
        drainer.drain( null, tupleQueues );
    }

    @Override
    public void ensureCapacity ( final int portIndex, final int capacity )
    {

    }

    @Override
    public void clear ()
    {

    }

    @Override
    public void setTupleCounts ( final int[] tupleCounts, final TupleAvailabilityByPort tupleAvailabilityByPort )
    {

    }

    @Override
    public void enableCapacityCheck ( final int portIndex )
    {

    }

    @Override
    public void disableCapacityCheck ( final int portIndex )
    {

    }

    @Override
    public boolean isCapacityCheckEnabled ( final int portIndex )
    {
        return false;
    }

    @Override
    public boolean isOverloaded ()
    {
        return false;
    }

    @Override
    public boolean isEmpty ()
    {
        return true;
    }

}

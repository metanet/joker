package cs.bilkent.joker.engine.tuplequeue.impl.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.NotThreadSafe;

import static com.google.common.base.Preconditions.checkArgument;
import cs.bilkent.joker.engine.tuplequeue.TupleQueue;
import cs.bilkent.joker.operator.Tuple;

@NotThreadSafe
public class SingleThreadedTupleQueue implements TupleQueue
{

    private final ArrayDeque<Tuple> queue;

    public SingleThreadedTupleQueue ( final int initialCapacity )
    {
        checkArgument( initialCapacity > 0 );
        this.queue = new ArrayDeque<>( initialCapacity );
    }

    @Override
    public void ensureCapacity ( final int capacity )
    {

    }

    @Override
    public void enableCapacityCheck ()
    {

    }

    @Override
    public void disableCapacityCheck ()
    {

    }

    @Override
    public boolean isCapacityCheckEnabled ()
    {
        return false;
    }

    @Override
    public void offerTuple ( final Tuple tuple )
    {
        queue.offer( tuple );
    }

    @Override
    public boolean tryOfferTuple ( final Tuple tuple, final long timeout, final TimeUnit unit )
    {
        offerTuple( tuple );
        return true;
    }

    @Override
    public void forceOfferTuple ( final Tuple tuple )
    {
        offerTuple( tuple );
    }

    @Override
    public void offerTuples ( final List<Tuple> tuples )
    {
        queue.addAll( tuples );
    }

    @Override
    public int tryOfferTuples ( final List<Tuple> tuples, final long timeout, final TimeUnit unit )
    {
        offerTuples( tuples );
        return tuples.size();
    }

    @Override
    public void forceOfferTuples ( final List<Tuple> tuples )
    {
        offerTuples( tuples );
    }

    @Override
    public List<Tuple> pollTuples ( final int count )
    {
        return doPollTuples( count, null );
    }

    @Override
    public List<Tuple> pollTuples ( final int count, final long timeout, final TimeUnit unit )
    {
        return doPollTuples( count, null );
    }

    @Override
    public void pollTuples ( final int count, final List<Tuple> tuples )
    {
        doPollTuples( count, tuples );
    }

    @Override
    public void pollTuples ( final int count, final List<Tuple> tuples, final long timeout, final TimeUnit unit )
    {
        doPollTuples( count, tuples );
    }

    private List<Tuple> doPollTuples ( final int count, List<Tuple> tuples )
    {
        if ( size() >= count )
        {
            if ( tuples == null )
            {
                tuples = new ArrayList<>( count );
            }

            for ( int i = 0; i < count; i++ )
            {
                tuples.add( queue.poll() );
            }
        }
        else if ( tuples == null )
        {
            tuples = Collections.emptyList();
        }

        return tuples;
    }

    @Override
    public List<Tuple> pollTuplesAtLeast ( final int count )
    {
        return doPollTuplesAtLeast( count, Integer.MAX_VALUE, null );
    }

    @Override
    public List<Tuple> pollTuplesAtLeast ( final int count, final long timeout, final TimeUnit unit )
    {
        return doPollTuplesAtLeast( count, Integer.MAX_VALUE, null );
    }

    @Override
    public List<Tuple> pollTuplesAtLeast ( final int count, final int limit )
    {
        return doPollTuplesAtLeast( count, limit, null );
    }

    @Override
    public List<Tuple> pollTuplesAtLeast ( final int count, final int limit, final long timeout, final TimeUnit unit )
    {
        checkArgument( limit >= count );
        return doPollTuplesAtLeast( count, limit, null );
    }

    @Override
    public void pollTuplesAtLeast ( final int count, final List<Tuple> tuples )
    {
        doPollTuplesAtLeast( count, Integer.MAX_VALUE, tuples );
    }

    @Override
    public void pollTuplesAtLeast ( final int count, final List<Tuple> tuples, final long timeout, final TimeUnit unit )
    {
        doPollTuplesAtLeast( count, Integer.MAX_VALUE, tuples );
    }

    @Override
    public void pollTuplesAtLeast ( final int count, final int limit, final List<Tuple> tuples )
    {
        doPollTuplesAtLeast( count, limit, tuples );
    }

    @Override
    public void pollTuplesAtLeast ( final int count, final int limit, final List<Tuple> tuples, final long timeout, final TimeUnit unit )
    {
        checkArgument( limit >= count );
        doPollTuplesAtLeast( count, limit, tuples );
    }

    private List<Tuple> doPollTuplesAtLeast ( final int count, int limit, List<Tuple> tuples )
    {
        if ( size() >= count )
        {
            if ( tuples == null )
            {
                tuples = new ArrayList<>( count );
            }

            final Iterator<Tuple> it = queue.iterator();
            while ( it.hasNext() && limit-- > 0 )
            {
                tuples.add( it.next() );
                it.remove();
            }
        }
        else if ( tuples == null )
        {
            tuples = Collections.emptyList();
        }

        return tuples;
    }

    @Override
    public boolean awaitMinimumSize ( final int expectedSize )
    {
        return queue.size() >= expectedSize;
    }

    @Override
    public boolean awaitMinimumSize ( final int expectedSize, final long timeout, final TimeUnit unit )
    {
        return queue.size() >= expectedSize;
    }

    @Override
    public int size ()
    {
        return queue.size();
    }

    @Override
    public void clear ()
    {
        queue.clear();
    }

}
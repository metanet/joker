package cs.bilkent.zanza.operator.impl;

import cs.bilkent.zanza.operator.InvocationContext;
import cs.bilkent.zanza.operator.kvstore.KVStore;
import cs.bilkent.zanza.operator.scheduling.SchedulingStrategy;


public class InvocationContextImpl implements InvocationContext
{

    private InvocationReason reason;

    private TuplesImpl input;

    private TuplesImpl output;

    private KVStore kvStore;

    private SchedulingStrategy schedulingStrategy;

    private boolean[] upstreamConnectionStatuses;

    public InvocationContextImpl ()
    {
    }

    public InvocationContextImpl ( final InvocationReason reason, final TuplesImpl input, final TuplesImpl output )
    {
        this.reason = reason;
        this.input = input;
        this.output = output;
    }

    public InvocationContextImpl ( final InvocationReason reason, final TuplesImpl input, final TuplesImpl output, final KVStore kvStore )
    {
        this.input = input;
        this.output = output;
        this.reason = reason;
        this.kvStore = kvStore;
    }

    public void setReason ( final InvocationReason reason )
    {
        this.reason = reason;
    }

    public void setInvocationParameters ( final InvocationReason reason,
                                          final TuplesImpl input,
                                          final TuplesImpl output,
                                          final KVStore kvStore )
    {
        this.reason = reason;
        this.input = input;
        this.output = output;
        this.kvStore = kvStore;
    }

    public void setUpstreamConnectionStatuses ( final boolean[] upstreamConnectionStatuses )
    {
        this.upstreamConnectionStatuses = upstreamConnectionStatuses;
    }

    public SchedulingStrategy getSchedulingStrategy ()
    {
        return schedulingStrategy;
    }

    @Override
    public TuplesImpl getInput ()
    {
        return input;
    }

    @Override
    public InvocationReason getReason ()
    {
        return reason;
    }

    @Override
    public boolean isInputPortOpen ( final int portIndex )
    {
        return upstreamConnectionStatuses[ portIndex ];
    }

    @Override
    public boolean isInputPortClosed ( final int portIndex )
    {
        return !upstreamConnectionStatuses[ portIndex ];
    }

    @Override
    public KVStore getKVStore ()
    {
        return kvStore;
    }

    @Override
    public TuplesImpl getOutput ()
    {
        return output;
    }

    @Override
    public void setNextSchedulingStrategy ( final SchedulingStrategy schedulingStrategy )
    {
        this.schedulingStrategy = schedulingStrategy;
    }

}

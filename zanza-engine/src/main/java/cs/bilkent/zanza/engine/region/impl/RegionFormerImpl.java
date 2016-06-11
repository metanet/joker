package cs.bilkent.zanza.engine.region.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cs.bilkent.zanza.engine.region.RegionDefinition;
import cs.bilkent.zanza.engine.region.RegionFormer;
import cs.bilkent.zanza.flow.FlowDefinition;
import cs.bilkent.zanza.flow.OperatorDefinition;
import cs.bilkent.zanza.flow.Port;
import cs.bilkent.zanza.operator.schema.runtime.PortRuntimeSchema;
import cs.bilkent.zanza.operator.spec.OperatorType;
import static cs.bilkent.zanza.operator.spec.OperatorType.PARTITIONED_STATEFUL;
import static cs.bilkent.zanza.operator.spec.OperatorType.STATEFUL;
import static cs.bilkent.zanza.operator.spec.OperatorType.STATELESS;
import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;
import static java.util.Collections.singletonList;

public class RegionFormerImpl implements RegionFormer
{

    private static final Logger LOGGER = LoggerFactory.getLogger( RegionFormerImpl.class );


    @Override
    public List<RegionDefinition> createRegions ( final FlowDefinition flow )
    {
        final List<RegionDefinition> regions = new ArrayList<>();

        for ( List<OperatorDefinition> operatorSequence : createOperatorSequences( flow ) )
        {
            regions.addAll( createRegions( operatorSequence ) );
        }

        return regions;
    }

    List<RegionDefinition> createRegions ( final List<OperatorDefinition> operatorSequence )
    {
        final List<RegionDefinition> regions = new ArrayList<>();

        OperatorType regionType = null;
        List<String> regionPartitionFieldNames = new ArrayList<>();
        List<OperatorDefinition> regionOperators = new ArrayList<>();

        for ( OperatorDefinition currentOperator : operatorSequence )
        {
            LOGGER.debug( "currentOperatorId={}", currentOperator.id() );

            final OperatorType operatorType = currentOperator.operatorType();

            if ( operatorType == STATEFUL )
            {
                if ( regionType != null ) // finalize current region
                {
                    regions.add( new RegionDefinition( regionType, regionPartitionFieldNames, regionOperators ) );
                    regionType = null;
                    regionPartitionFieldNames = new ArrayList<>();
                    regionOperators = new ArrayList<>();
                }

                regions.add( new RegionDefinition( STATEFUL, emptyList(), singletonList( currentOperator ) ) ); // add operator as a region
            }
            else if ( operatorType == STATELESS )
            {
                if ( regionType == null )
                {
                    regionType = STATELESS;
                }

                regionOperators.add( currentOperator );
            }
            else if ( operatorType == PARTITIONED_STATEFUL )
            {
                if ( regionType == null )
                {
                    regionType = PARTITIONED_STATEFUL;
                    regionPartitionFieldNames.addAll( currentOperator.partitionFieldNames() );
                    regionOperators.add( currentOperator );
                }
                else if ( regionType == PARTITIONED_STATEFUL )
                {
                    regions.add( new RegionDefinition( PARTITIONED_STATEFUL, regionPartitionFieldNames, regionOperators ) );
                    regionPartitionFieldNames = new ArrayList<>( currentOperator.partitionFieldNames() );
                    regionOperators = new ArrayList<>();
                    regionOperators.add( currentOperator );
                }
                else
                {
                    // regionType is STATELESS
                    final ListIterator<OperatorDefinition> it = regionOperators.listIterator( regionOperators.size() );
                    final List<OperatorDefinition> newRegionOperators = new ArrayList<>();
                    while ( it.hasPrevious() )
                    {
                        final OperatorDefinition prev = it.previous();
                        if ( containsAllFieldNamesOnInputPort( prev, currentOperator.partitionFieldNames() ) )
                        {
                            newRegionOperators.add( prev );
                            it.remove();
                        }
                        else
                        {
                            break;
                        }
                    }

                    if ( !regionOperators.isEmpty() )
                    {
                        regions.add( new RegionDefinition( STATELESS, emptyList(), regionOperators ) );
                    }

                    regionType = PARTITIONED_STATEFUL;
                    reverse( newRegionOperators );
                    newRegionOperators.add( currentOperator );
                    regionOperators = newRegionOperators;
                    regionPartitionFieldNames = new ArrayList<>( currentOperator.partitionFieldNames() );
                }
            }
            else
            {
                throw new IllegalArgumentException( "Invalid operator type for " + currentOperator.id() );
            }
        }

        if ( regionType != null )
        {
            regions.add( new RegionDefinition( regionType, regionPartitionFieldNames, regionOperators ) );
        }

        return regions;
    }

    Collection<List<OperatorDefinition>> createOperatorSequences ( final FlowDefinition flow )
    {

        final Collection<List<OperatorDefinition>> sequences = new ArrayList<>();

        final Set<OperatorDefinition> processedSequenceStartOperators = new HashSet<>();
        final Set<OperatorDefinition> sequenceStartOperators = new HashSet<>( flow.getOperatorsWithNoInputPorts() );
        List<OperatorDefinition> currentOperatorSequence = new ArrayList<>();

        OperatorDefinition operator;
        while ( ( operator = removeRandomOperator( sequenceStartOperators ) ) != null )
        {
            processedSequenceStartOperators.add( operator );
            LOGGER.debug( "Starting new sequence with oid={}", operator.id() );

            while ( true )
            {
                currentOperatorSequence.add( operator );
                LOGGER.debug( "Adding oid={} to current sequence", operator.id() );

                final Collection<OperatorDefinition> downstreamOperators = flow.getDownstreamOperators( operator.id() );
                final OperatorDefinition downstreamOperator = getDownstreamOperatorIfSingle( flow, downstreamOperators );
                if ( downstreamOperator != null )
                {
                    operator = downstreamOperator;
                }
                else
                {
                    downstreamOperators.removeAll( processedSequenceStartOperators );
                    sequenceStartOperators.addAll( downstreamOperators );
                    sequences.add( currentOperatorSequence );

                    LOGGER.debug( "Sequence Completed! Sequence={}. New sequence starts={}",
                                  getOperatorIds( currentOperatorSequence ),
                                  getOperatorIds( downstreamOperators ) );
                    currentOperatorSequence = new ArrayList<>();
                    break;
                }
            }
        }

        return sequences;
    }

    private boolean containsAllFieldNamesOnInputPort ( final OperatorDefinition operator, final List<String> fieldNames )
    {
        if ( operator.inputPortCount() == 1 )
        {
            final PortRuntimeSchema inputSchema = operator.schema().getInputSchema( 0 );
            return fieldNames.stream().allMatch( fieldName -> inputSchema.getField( fieldName ) != null );
        }
        else
        {
            return false;
        }
    }

    private OperatorDefinition removeRandomOperator ( final Set<OperatorDefinition> operators )
    {
        OperatorDefinition operator = null;
        final Iterator<OperatorDefinition> it = operators.iterator();
        if ( it.hasNext() )
        {
            operator = it.next();
            it.remove();
        }

        return operator;
    }

    private OperatorDefinition getDownstreamOperatorIfSingle ( final FlowDefinition flow,
                                                               final Collection<OperatorDefinition> downstreamOperators )
    {
        if ( downstreamOperators.size() == 1 )
        {
            final OperatorDefinition next = downstreamOperators.iterator().next();
            final Collection<Port> upstreamPorts = flow.getUpstreamConnections( next.id() );
            return upstreamPorts.size() == 1 ? next : null;
        }
        else
        {
            return null;
        }
    }

    private Stream<String> getOperatorIds ( final Collection<OperatorDefinition> operators )
    {
        return operators.stream().map( OperatorDefinition::id );
    }

}

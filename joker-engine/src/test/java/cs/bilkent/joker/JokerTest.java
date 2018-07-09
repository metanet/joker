package cs.bilkent.joker;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import cs.bilkent.joker.Joker.JokerBuilder;
import cs.bilkent.joker.engine.config.JokerConfig;
import cs.bilkent.joker.engine.flow.FlowExecPlan;
import cs.bilkent.joker.engine.flow.PipelineId;
import cs.bilkent.joker.engine.flow.RegionDef;
import cs.bilkent.joker.engine.flow.RegionExecPlan;
import cs.bilkent.joker.engine.region.impl.AbstractRegionExecPlanFactory;
import cs.bilkent.joker.flow.FlowDef;
import cs.bilkent.joker.flow.FlowDefBuilder;
import cs.bilkent.joker.operator.InitCtx;
import cs.bilkent.joker.operator.InvocationCtx;
import cs.bilkent.joker.operator.Operator;
import cs.bilkent.joker.operator.OperatorConfig;
import cs.bilkent.joker.operator.OperatorDef;
import cs.bilkent.joker.operator.OperatorDefBuilder;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.operator.kvstore.KVStore;
import static cs.bilkent.joker.operator.scheduling.ScheduleWhenTuplesAvailable.TupleAvailabilityByCount.AT_LEAST;
import static cs.bilkent.joker.operator.scheduling.ScheduleWhenTuplesAvailable.scheduleWhenTuplesAvailableOnAll;
import static cs.bilkent.joker.operator.scheduling.ScheduleWhenTuplesAvailable.scheduleWhenTuplesAvailableOnDefaultPort;
import cs.bilkent.joker.operator.scheduling.SchedulingStrategy;
import cs.bilkent.joker.operator.schema.runtime.OperatorRuntimeSchemaBuilder;
import cs.bilkent.joker.operator.schema.runtime.TupleSchema;
import cs.bilkent.joker.operator.spec.OperatorSpec;
import static cs.bilkent.joker.operator.spec.OperatorType.PARTITIONED_STATEFUL;
import static cs.bilkent.joker.operator.spec.OperatorType.STATELESS;
import cs.bilkent.joker.operators.BeaconOperator;
import static cs.bilkent.joker.operators.BeaconOperator.TUPLE_COUNT_CONFIG_PARAMETER;
import static cs.bilkent.joker.operators.BeaconOperator.TUPLE_POPULATOR_CONFIG_PARAMETER;
import cs.bilkent.joker.operators.ForEachOperator;
import static cs.bilkent.joker.operators.ForEachOperator.CONSUMER_FUNCTION_CONFIG_PARAMETER;
import cs.bilkent.joker.operators.MapperOperator;
import static cs.bilkent.joker.operators.MapperOperator.MAPPER_CONFIG_PARAMETER;
import cs.bilkent.joker.test.AbstractJokerTest;
import cs.bilkent.joker.test.category.SlowTest;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

public class JokerTest extends AbstractJokerTest
{

    private static final int PARTITIONED_STATEFUL_REGION_REPLICA_COUNT = 4;

    private static final int KEY_RANGE = 10000;

    private static final int VALUE_RANGE = 5;

    private static final int MULTIPLIER_VALUE = 100;

    @Test
    public void testEndToEndSystem () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();

        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory regionExecPlanFactory = new StaticRegionExecPlanFactory( jokerConfig,
                                                                                                   PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        joker.run( ex.flow );

        sleepUninterruptibly( 30, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithStaticFlowOptimization () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample2 ex = new FlowExample2();

        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory regionExecPlanFactory = new StaticRegionExecPlanFactory( jokerConfig,
                                                                                                   PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        joker.run( ex.flow );

        sleepUninterruptibly( 300, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector1 is invoked " + ex.valueCollector1.invocationCount.get() + " times." );
        System.out.println( "Collector2 is invoked " + ex.valueCollector2.invocationCount.get() + " times." );
        System.out.println( "Collector3 is invoked " + ex.valueCollector3.invocationCount.get() + " times." );
        System.out.println( "Collector4 is invoked " + ex.valueCollector4.invocationCount.get() + " times." );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector1 is invoked " + ex.valueCollector1.invocationCount.get() + " times." );
        System.out.println( "Collector2 is invoked " + ex.valueCollector2.invocationCount.get() + " times." );
        System.out.println( "Collector3 is invoked " + ex.valueCollector3.invocationCount.get() + " times." );
        System.out.println( "Collector4 is invoked " + ex.valueCollector4.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector1.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual1 = ex.valueCollector1.values.get( i );
            final int actual2 = ex.valueCollector2.values.get( i );
            final int actual3 = ex.valueCollector3.values.get( i );
            final int actual4 = ex.valueCollector4.values.get( i );
            assertEquals( expected, actual1 );
            assertEquals( expected, actual2 );
            assertEquals( expected, actual3 );
            assertEquals( expected, actual4 );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithMergingPipelines () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory regionExecPlanFactory = new StaticRegionExecPlanFactory( jokerConfig,
                                                                                                   PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        final FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        final RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        final List<PipelineId> pipelineIdsToMerge = regionExecPlan.getPipelineIds();
        checkState( pipelineIdsToMerge.size() > 1 );

        joker.mergePipelines( flowExecPlan.getVersion(), pipelineIdsToMerge ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithSplittingPipelines () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory2 regionExecPlanFactory = new StaticRegionExecPlanFactory2( jokerConfig,
                                                                                                     PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        final FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        final RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        joker.splitPipeline( flowExecPlan.getVersion(), regionExecPlan.getPipelineIds().get( 0 ), asList( 1, 2 ) ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithSplittingAndMergingPipelines () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory2 regionExecPlanFactory = new StaticRegionExecPlanFactory2( jokerConfig,
                                                                                                     PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        checkState( regionExecPlan.getPipelineIds().size() == 1 );
        flowExecPlan = joker.splitPipeline( flowExecPlan.getVersion(), regionExecPlan.getPipelineIds().get( 0 ), asList( 1, 2 ) )
                            .get( 15, SECONDS );
        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        checkState( regionExecPlan.getPipelineIds().size() == 3 );

        sleepUninterruptibly( 15, SECONDS );

        joker.mergePipelines( flowExecPlan.getVersion(), regionExecPlan.getPipelineIds() ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithMergingAndSplittingPipelines () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory regionExecPlanFactory = new StaticRegionExecPlanFactory( jokerConfig,
                                                                                                   PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        final List<PipelineId> pipelineIdsToMerge = regionExecPlan.getPipelineIds();
        checkState( pipelineIdsToMerge.size() == 2 );

        flowExecPlan = joker.mergePipelines( flowExecPlan.getVersion(), pipelineIdsToMerge ).get( 15, SECONDS );
        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        final List<PipelineId> pipelineIdsToSplit = regionExecPlan.getPipelineIds();
        checkState( pipelineIdsToSplit.size() == 1 );

        sleepUninterruptibly( 15, SECONDS );

        joker.splitPipeline( flowExecPlan.getVersion(), pipelineIdsToSplit.get( 0 ), asList( 1, 2 ) ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithRebalancingRegions () throws InterruptedException, ExecutionException, TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory2 regionExecPlanFactory = new StaticRegionExecPlanFactory2( jokerConfig,
                                                                                                     PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        flowExecPlan = joker.rebalanceRegion( flowExecPlan.getVersion(),
                                              regionExecPlan.getRegionId(),
                                              regionExecPlan.getReplicaCount() / 2 ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        flowExecPlan = joker.rebalanceRegion( flowExecPlan.getVersion(),
                                              regionExecPlan.getRegionId(),
                                              regionExecPlan.getReplicaCount() * 2 ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        flowExecPlan = joker.rebalanceRegion( flowExecPlan.getVersion(),
                                              regionExecPlan.getRegionId(),
                                              regionExecPlan.getReplicaCount() / 2 ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        joker.rebalanceRegion( flowExecPlan.getVersion(), regionExecPlan.getRegionId(), regionExecPlan.getReplicaCount() * 2 )
             .get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    @Category( SlowTest.class )
    @Test
    public void testEndToEndSystemWithRebalancingRegionsAndMergingSplittingPipelines () throws InterruptedException, ExecutionException,
                                                                                                           TimeoutException
    {
        final FlowExample1 ex = new FlowExample1();
        final JokerConfig jokerConfig = new JokerConfig();
        final StaticRegionExecPlanFactory2 regionExecPlanFactory = new StaticRegionExecPlanFactory2( jokerConfig,
                                                                                                     PARTITIONED_STATEFUL_REGION_REPLICA_COUNT );
        final Joker joker = new JokerBuilder().setRegionExecPlanFactory( regionExecPlanFactory ).setJokerConfig( jokerConfig ).build();

        FlowExecPlan flowExecPlan = joker.run( ex.flow );

        sleepUninterruptibly( 15, SECONDS );

        RegionExecPlan regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        flowExecPlan = joker.rebalanceRegion( flowExecPlan.getVersion(),
                                              regionExecPlan.getRegionId(),
                                              regionExecPlan.getReplicaCount() / 2 ).get( 60, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        final List<PipelineId> pipelineIdsToSplit = regionExecPlan.getPipelineIds();
        checkState( pipelineIdsToSplit.size() == 1 );
        flowExecPlan = joker.splitPipeline( flowExecPlan.getVersion(), pipelineIdsToSplit.get( 0 ), asList( 1, 2 ) ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        final List<PipelineId> pipelineIdsToMerge = regionExecPlan.getPipelineIds();
        checkState( pipelineIdsToMerge.size() == 3 );
        flowExecPlan = joker.mergePipelines( flowExecPlan.getVersion(), pipelineIdsToMerge ).get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        regionExecPlan = flowExecPlan.getRegionExecPlan( ex.join.getId() );
        joker.rebalanceRegion( flowExecPlan.getVersion(), regionExecPlan.getRegionId(), regionExecPlan.getReplicaCount() * 2 )
             .get( 15, SECONDS );

        sleepUninterruptibly( 15, SECONDS );

        joker.shutdown().get( 60, SECONDS );

        System.out.println( "Value generator 1 is invoked " + ex.valueGenerator1.invocationCount.get() + " times." );
        System.out.println( "Value generator 2 is invoked " + ex.valueGenerator2.invocationCount.get() + " times." );
        System.out.println( "Collector is invoked " + ex.valueCollector.invocationCount.get() + " times." );

        for ( int i = 0; i < ex.valueCollector.values.length(); i++ )
        {
            final int expected = ( ex.valueGenerator1.generatedValues[ i ].intValue() + ex.valueGenerator2.generatedValues[ i ].intValue() )
                                 * MULTIPLIER_VALUE;
            final int actual = ex.valueCollector.values.get( i );
            assertEquals( "i: " + i + " expected: " + expected + " actual: " + actual, expected, actual );
        }
    }

    static class StaticRegionExecPlanFactory extends AbstractRegionExecPlanFactory
    {

        private final int replicaCount;

        StaticRegionExecPlanFactory ( final JokerConfig jokerConfig, final int replicaCount )
        {
            super( jokerConfig );
            this.replicaCount = replicaCount;
        }

        @Override
        protected RegionExecPlan createRegionExecPlan ( final RegionDef regionDef )
        {
            final int replicaCount = regionDef.getRegionType() == PARTITIONED_STATEFUL ? this.replicaCount : 1;
            final int operatorCount = regionDef.getOperatorCount();
            final List<Integer> pipelineStartIndices = operatorCount == 1 ? singletonList( 0 ) : asList( 0, operatorCount / 2 );

            return new RegionExecPlan( regionDef, pipelineStartIndices, replicaCount );
        }
    }


    public static class StaticRegionExecPlanFactory2 extends AbstractRegionExecPlanFactory
    {

        private final int replicaCount;

        public StaticRegionExecPlanFactory2 ( final JokerConfig jokerConfig, final int replicaCount )
        {
            super( jokerConfig );
            this.replicaCount = replicaCount;
        }

        @Override
        protected RegionExecPlan createRegionExecPlan ( final RegionDef regionDef )
        {
            final int replicaCount = regionDef.getRegionType() == PARTITIONED_STATEFUL ? this.replicaCount : 1;
            return new RegionExecPlan( regionDef, singletonList( 0 ), replicaCount );
        }
    }


    static class ValueGenerator implements Consumer<Tuple>
    {

        static final Random RANDOM = new Random();

        private final int keyRange;

        private final int valueRange;

        private final AtomicInteger[] generatedValues;

        private final AtomicInteger invocationCount = new AtomicInteger();

        ValueGenerator ( final int keyRange, final int valueRange )
        {
            this.keyRange = keyRange;
            this.valueRange = valueRange;
            this.generatedValues = new AtomicInteger[ keyRange ];
            for ( int i = 0; i < keyRange; i++ )
            {
                this.generatedValues[ i ] = new AtomicInteger( 0 );
            }
        }

        @Override
        public void accept ( final Tuple tuple )
        {
//            LockSupport.parkNanos( 1000 );

            invocationCount.incrementAndGet();

            final int key = RANDOM.nextInt( keyRange );
            final int value = RANDOM.nextInt( valueRange ) + 1;

            final AtomicInteger valueHolder = generatedValues[ key ];
            final int existing = valueHolder.get();
            valueHolder.set( existing + value );

            tuple.set( "key", key ).set( "value", value );
        }

    }


    static class ValueCollector implements Consumer<Tuple>
    {

        private final AtomicReferenceArray<Integer> values;

        private final AtomicInteger invocationCount = new AtomicInteger();

        private final String name;

        ValueCollector ( final String name, final int keyRange )
        {
            this.name = name;
            this.values = new AtomicReferenceArray<>( keyRange );
            for ( int i = 0; i < keyRange; i++ )
            {
                this.values.set( i, 0 );
            }
        }

        @Override
        public void accept ( final Tuple tuple )
        {
            invocationCount.incrementAndGet();

            final Integer key = tuple.getInteger( "key" );
            final Integer newVal = tuple.getInteger( "mult" );
            final Integer curr = values.get( key );
            if ( curr > newVal )
            {
                //                System.err.println( "ERR in " + name + "! key: " + key + " curr: " + curr + " new: " + newVal );
                return;
            }
            values.set( key, newVal );
        }

    }


    @OperatorSpec( inputPortCount = 2, outputPortCount = 1, type = PARTITIONED_STATEFUL )
    public static class JoinOperator implements Operator
    {

        @Override
        public SchedulingStrategy init ( final InitCtx ctx )
        {
            return scheduleWhenTuplesAvailableOnAll( AT_LEAST, 2, 1, 0, 1 );
        }

        @Override
        public void invoke ( final InvocationCtx ctx )
        {
            final List<Tuple> tuples0 = ctx.getInputTuples( 0 );
            final List<Tuple> tuples1 = ctx.getInputTuples( 1 );
            final int c = Math.min( tuples0.size(), tuples1.size() );

            for ( int i = 0; i < c; i++ )
            {
                final Tuple input0 = tuples0.get( i );
                final Tuple input1 = tuples1.get( i );
                final Tuple output0 = input0.shallowCopy();
                output0.attachTo( input1 );
                ctx.output( output0 );
                final Tuple output1 = input1.shallowCopy();
                output1.attachTo( input0 );
                ctx.output( output1 );
            }

            for ( int i = c; i < tuples0.size(); i++ )
            {
                ctx.output( tuples0.get( i ) );
            }

            for ( int i = c; i < tuples1.size(); i++ )
            {
                ctx.output( tuples1.get( i ) );
            }
        }

    }


    @OperatorSpec( inputPortCount = 1, outputPortCount = 1, type = PARTITIONED_STATEFUL )
    public static class SummerOperator implements Operator
    {

        private TupleSchema outputSchema;

        @Override
        public SchedulingStrategy init ( final InitCtx ctx )
        {
            outputSchema = ctx.getOutputPortSchema( 0 );
            return scheduleWhenTuplesAvailableOnDefaultPort( 1 );
        }

        @Override
        public void invoke ( final InvocationCtx ctx )
        {
            final KVStore kvStore = ctx.getKVStore();

            for ( Tuple input : ctx.getInputTuples( 0 ) )
            {
                final Object key = input.get( "key" );
                final int currSum = kvStore.getIntegerValueOrDefault( key, 0 );
                final int newSum = currSum + input.getInteger( "value" );

                kvStore.set( key, newSum );

                final Tuple result = Tuple.of( outputSchema, "key", key, "sum", newSum );
                result.attachTo( input );
                ctx.output( result );
            }
        }

    }


    @OperatorSpec( inputPortCount = 1, outputPortCount = 1, type = STATELESS )
    public static class ValuePasserOperator implements Operator
    {

        @Override
        public SchedulingStrategy init ( final InitCtx ctx )
        {
            return scheduleWhenTuplesAvailableOnDefaultPort( 1 );
        }

        @Override
        public void invoke ( final InvocationCtx ctx )
        {
            ctx.getInputTuplesByDefaultPort().forEach( ctx::output );
        }

    }


    private static class FlowExample1
    {

        final ValueGenerator valueGenerator1 = new ValueGenerator( KEY_RANGE, VALUE_RANGE );

        final ValueGenerator valueGenerator2 = new ValueGenerator( KEY_RANGE, VALUE_RANGE );

        final ValueCollector valueCollector = new ValueCollector( "valueCollector", KEY_RANGE );

        final OperatorDef join;

        final FlowDef flow;

        FlowExample1 ()
        {
            final OperatorConfig beacon1Config = new OperatorConfig().set( TUPLE_POPULATOR_CONFIG_PARAMETER, valueGenerator1 )
                                                                     .set( TUPLE_COUNT_CONFIG_PARAMETER, 20 );

            final OperatorRuntimeSchemaBuilder beacon1Schema = new OperatorRuntimeSchemaBuilder( 0, 1 );
            beacon1Schema.getOutputPortSchemaBuilder( 0 ).addField( "key", Integer.class ).addField( "value", Integer.class );

            final OperatorDef beacon1 = OperatorDefBuilder.newInstance( "beacon1", BeaconOperator.class )
                                                          .setConfig( beacon1Config )
                                                          .setExtendingSchema( beacon1Schema )
                                                          .build();

            final OperatorConfig beacon2Config = new OperatorConfig().set( TUPLE_POPULATOR_CONFIG_PARAMETER, valueGenerator2 )
                                                                     .set( TUPLE_COUNT_CONFIG_PARAMETER, 10 );

            final OperatorRuntimeSchemaBuilder beacon2Schema = new OperatorRuntimeSchemaBuilder( 0, 1 );
            beacon2Schema.getOutputPortSchemaBuilder( 0 ).addField( "key", Integer.class ).addField( "value", Integer.class );

            final OperatorDef beacon2 = OperatorDefBuilder.newInstance( "beacon2", BeaconOperator.class )
                                                          .setConfig( beacon2Config )
                                                          .setExtendingSchema( beacon2Schema )
                                                          .build();

            final OperatorRuntimeSchemaBuilder joinSchema = new OperatorRuntimeSchemaBuilder( 2, 1 );
            joinSchema.addInputField( 0, "key", Integer.class )
                      .addInputField( 0, "value", Integer.class )
                      .addInputField( 1, "key", Integer.class )
                      .addInputField( 1, "value", Integer.class )
                      .addOutputField( 0, "key", Integer.class )
                      .addOutputField( 0, "value", Integer.class );

            join = OperatorDefBuilder.newInstance( "joiner", JoinOperator.class )
                                     .setExtendingSchema( joinSchema )
                                     .setPartitionFieldNames( singletonList( "key" ) )
                                     .build();

            final OperatorRuntimeSchemaBuilder summerSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            summerSchema.addInputField( 0, "key", Integer.class )
                        .addInputField( 0, "value", Integer.class )
                        .addOutputField( 0, "key", Integer.class )
                        .addOutputField( 0, "sum", Integer.class );

            final OperatorDef summer = OperatorDefBuilder.newInstance( "summer", SummerOperator.class )
                                                         .setExtendingSchema( summerSchema )
                                                         .setPartitionFieldNames( singletonList( "key" ) )
                                                         .build();

            final OperatorConfig multiplierConfig = new OperatorConfig().set( MAPPER_CONFIG_PARAMETER,
                                                                              (BiConsumer<Tuple, Tuple>) ( input, output ) -> {
                                                                                  output.set( "key", input.get( "key" ) )
                                                                                        .set( "mult",
                                                                                              MULTIPLIER_VALUE
                                                                                              * input.getInteger( "sum" ) );
                                                                              } );

            final OperatorRuntimeSchemaBuilder multiplierSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            multiplierSchema.addInputField( 0, "key", Integer.class )
                            .addInputField( 0, "sum", Integer.class )
                            .addOutputField( 0, "key", Integer.class )
                            .addOutputField( 0, "mult", Integer.class );

            final OperatorDef multiplier = OperatorDefBuilder.newInstance( "multiplier", MapperOperator.class )
                                                             .setConfig( multiplierConfig )
                                                             .setExtendingSchema( multiplierSchema )
                                                             .build();

            final OperatorConfig collectorConfig = new OperatorConfig();
            collectorConfig.set( CONSUMER_FUNCTION_CONFIG_PARAMETER, valueCollector );

            final OperatorRuntimeSchemaBuilder foreachSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            foreachSchema.addInputField( 0, "key", Integer.class ).addInputField( 0, "mult", Integer.class );

            final OperatorDef collector = OperatorDefBuilder.newInstance( "collector", ForEachOperator.class )
                                                            .setConfig( collectorConfig )
                                                            .setExtendingSchema( foreachSchema )
                                                            .build();

            flow = new FlowDefBuilder().add( beacon1 )
                                       .add( beacon2 )
                                       .add( join )
                                       .add( summer )
                                       .add( multiplier )
                                       .add( collector )
                                       .connect( "beacon1", "joiner", 0 )
                                       .connect( "beacon2", "joiner", 1 )
                                       .connect( "joiner", "summer" )
                                       .connect( "summer", "multiplier" )
                                       .connect( "multiplier", "collector" )
                                       .build();
        }

    }


    private static class FlowExample2
    {

        final ValueGenerator valueGenerator1 = new ValueGenerator( KEY_RANGE, VALUE_RANGE );

        final ValueGenerator valueGenerator2 = new ValueGenerator( KEY_RANGE, VALUE_RANGE );

        final ValueCollector valueCollector1 = new ValueCollector( "valueCollector1", KEY_RANGE );

        final ValueCollector valueCollector2 = new ValueCollector( "valueCollector2", KEY_RANGE );

        final ValueCollector valueCollector3 = new ValueCollector( "valueCollector3", KEY_RANGE );

        final ValueCollector valueCollector4 = new ValueCollector( "valueCollector4", KEY_RANGE );

        final FlowDef flow;

        FlowExample2 ()
        {
            final OperatorConfig beacon1Config = new OperatorConfig().set( TUPLE_POPULATOR_CONFIG_PARAMETER, valueGenerator1 )
                                                                     .set( TUPLE_COUNT_CONFIG_PARAMETER, 2 );

            final OperatorRuntimeSchemaBuilder beacon1Schema = new OperatorRuntimeSchemaBuilder( 0, 1 );
            beacon1Schema.getOutputPortSchemaBuilder( 0 ).addField( "key", Integer.class ).addField( "value", Integer.class );

            final OperatorDef beacon1 = OperatorDefBuilder.newInstance( "beacon1", BeaconOperator.class )
                                                          .setConfig( beacon1Config )
                                                          .setExtendingSchema( beacon1Schema )
                                                          .build();

            final OperatorConfig beacon2Config = new OperatorConfig().set( TUPLE_POPULATOR_CONFIG_PARAMETER, valueGenerator2 )
                                                                     .set( TUPLE_COUNT_CONFIG_PARAMETER, 3 );

            final OperatorRuntimeSchemaBuilder beacon2Schema = new OperatorRuntimeSchemaBuilder( 0, 1 );
            beacon2Schema.getOutputPortSchemaBuilder( 0 ).addField( "key", Integer.class ).addField( "value", Integer.class );

            final OperatorDef beacon2 = OperatorDefBuilder.newInstance( "beacon2", BeaconOperator.class )
                                                          .setConfig( beacon2Config )
                                                          .setExtendingSchema( beacon2Schema )
                                                          .build();

            final OperatorRuntimeSchemaBuilder joinSchema = new OperatorRuntimeSchemaBuilder( 2, 1 );
            joinSchema.addInputField( 0, "key", Integer.class )
                      .addInputField( 0, "value", Integer.class )
                      .addInputField( 1, "key", Integer.class )
                      .addInputField( 1, "value", Integer.class )
                      .addOutputField( 0, "key", Integer.class )
                      .addOutputField( 0, "value", Integer.class );

            final OperatorDef join = OperatorDefBuilder.newInstance( "joiner", JoinOperator.class )
                                                       .setExtendingSchema( joinSchema )
                                                       .setPartitionFieldNames( singletonList( "key" ) )
                                                       .build();

            final OperatorRuntimeSchemaBuilder summerSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            summerSchema.addInputField( 0, "key", Integer.class )
                        .addInputField( 0, "value", Integer.class )
                        .addOutputField( 0, "key", Integer.class )
                        .addOutputField( 0, "sum", Integer.class );

            final OperatorDef summer = OperatorDefBuilder.newInstance( "summer", SummerOperator.class )
                                                         .setExtendingSchema( summerSchema )
                                                         .setPartitionFieldNames( singletonList( "key" ) )
                                                         .build();

            final OperatorConfig multiplierConfig = new OperatorConfig().set( MAPPER_CONFIG_PARAMETER,
                                                                              (BiConsumer<Tuple, Tuple>) ( input, output ) -> {
                                                                                  output.set( "key", input.get( "key" ) )
                                                                                        .set( "mult",
                                                                                              MULTIPLIER_VALUE
                                                                                              * input.getInteger( "sum" ) );
                                                                              } );

            final OperatorRuntimeSchemaBuilder multiplierSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            multiplierSchema.addInputField( 0, "key", Integer.class )
                            .addInputField( 0, "sum", Integer.class )
                            .addOutputField( 0, "key", Integer.class )
                            .addOutputField( 0, "mult", Integer.class );

            final OperatorDef multiplier = OperatorDefBuilder.newInstance( "multiplier", MapperOperator.class )
                                                             .setConfig( multiplierConfig )
                                                             .setExtendingSchema( multiplierSchema )
                                                             .build();

            final OperatorConfig collectorConfig1 = new OperatorConfig().set( CONSUMER_FUNCTION_CONFIG_PARAMETER, valueCollector1 );

            final OperatorRuntimeSchemaBuilder foreachSchema = new OperatorRuntimeSchemaBuilder( 1, 1 );
            foreachSchema.addInputField( 0, "key", Integer.class )
                         .addInputField( 0, "mult", Integer.class )
                         .addOutputField( 0, "key", Integer.class )
                         .addOutputField( 0, "mult", Integer.class );

            final OperatorDef collector1 = OperatorDefBuilder.newInstance( "collector1", ForEachOperator.class )
                                                             .setConfig( collectorConfig1 )
                                                             .setExtendingSchema( foreachSchema )
                                                             .build();

            final OperatorDef valuePasserStateless1 = OperatorDefBuilder.newInstance( "valuePasserStateless1", ValuePasserOperator.class )
                                                                        .setExtendingSchema( foreachSchema )
                                                                        .build();

            final OperatorConfig collectorConfig2 = new OperatorConfig().set( CONSUMER_FUNCTION_CONFIG_PARAMETER, valueCollector2 );

            final OperatorDef collector2 = OperatorDefBuilder.newInstance( "collector2", ForEachOperator.class )
                                                             .setConfig( collectorConfig2 )
                                                             .setExtendingSchema( foreachSchema )
                                                             .build();

            final OperatorConfig collectorConfig3 = new OperatorConfig().set( CONSUMER_FUNCTION_CONFIG_PARAMETER, valueCollector3 );

            final OperatorDef collector3 = OperatorDefBuilder.newInstance( "collector3", ForEachOperator.class )
                                                             .setConfig( collectorConfig3 )
                                                             .setExtendingSchema( foreachSchema )
                                                             .build();

            final OperatorConfig valuePasserStatefulConfig = new OperatorConfig().set( CONSUMER_FUNCTION_CONFIG_PARAMETER,
                                                                                       (Consumer<Tuple>) tuple -> {
                                                                                       } );

            final OperatorDef valuePasserStateful1 = OperatorDefBuilder.newInstance( "valuePasserStateful1", ForEachOperator.class )
                                                                       .setConfig( valuePasserStatefulConfig )
                                                                       .setExtendingSchema( foreachSchema )
                                                                       .build();

            final OperatorDef valuePasserStateful2 = OperatorDefBuilder.newInstance( "valuePasserStateful2", ForEachOperator.class )
                                                                       .setConfig( valuePasserStatefulConfig )
                                                                       .setExtendingSchema( foreachSchema )
                                                                       .build();

            final OperatorDef valuePasserStateless2 = OperatorDefBuilder.newInstance( "valuePasserStateless2", ValuePasserOperator.class )
                                                                        .setExtendingSchema( foreachSchema )
                                                                        .build();

            final OperatorConfig collectorConfig4 = new OperatorConfig().set( CONSUMER_FUNCTION_CONFIG_PARAMETER, valueCollector4 );

            final OperatorDef collector4 = OperatorDefBuilder.newInstance( "collector4", ForEachOperator.class )
                                                             .setConfig( collectorConfig4 )
                                                             .setExtendingSchema( foreachSchema )
                                                             .build();

            flow = new FlowDefBuilder().add( beacon1 )
                                       .add( beacon2 )
                                       .add( join )
                                       .add( summer )
                                       .add( multiplier )
                                       .add( collector1 )
                                       .add( valuePasserStateless1 )
                                       .add( collector2 )
                                       .add( collector3 )
                                       .add( valuePasserStateful1 )
                                       .add( valuePasserStateful2 )
                                       .add( valuePasserStateless2 )
                                       .add( collector4 )
                                       .connect( "beacon1", "joiner", 0 )
                                       .connect( "beacon2", "joiner", 1 )
                                       .connect( "joiner", "summer" )
                                       .connect( "summer", "multiplier" )
                                       .connect( "multiplier", "collector1" )
                                       .connect( "multiplier", "valuePasserStateless1" )
                                       .connect( "valuePasserStateless1", "collector2" )
                                       .connect( "valuePasserStateless1", "collector3" )
                                       .connect( "multiplier", "valuePasserStateful1" )
                                       .connect( "multiplier", "valuePasserStateful2" )
                                       .connect( "valuePasserStateful1", "valuePasserStateless2" )
                                       .connect( "valuePasserStateful2", "valuePasserStateless2" )
                                       .connect( "valuePasserStateless2", "collector4" )
                                       .build();
        }

    }

}

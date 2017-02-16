package cs.bilkent.joker.operators;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.Test;

import static cs.bilkent.joker.operator.InvocationContext.InvocationReason.SUCCESS;
import cs.bilkent.joker.operator.OperatorConfig;
import cs.bilkent.joker.operator.OperatorDef;
import cs.bilkent.joker.operator.OperatorDefBuilder;
import cs.bilkent.joker.operator.Tuple;
import cs.bilkent.joker.operator.impl.InitializationContextImpl;
import cs.bilkent.joker.operator.impl.InvocationContextImpl;
import cs.bilkent.joker.operator.impl.TuplesImpl;
import cs.bilkent.joker.operator.schema.runtime.OperatorRuntimeSchemaBuilder;
import static cs.bilkent.joker.operators.BeaconOperator.TUPLE_COUNT_CONFIG_PARAMETER;
import static cs.bilkent.joker.operators.BeaconOperator.TUPLE_POPULATOR_CONFIG_PARAMETER;
import cs.bilkent.joker.test.AbstractJokerTest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;


public class BeaconOperatorTest extends AbstractJokerTest
{

    private final Random random = new Random();


    @Test
    public void shouldGenerateTuplesWithRandomCountField () throws InstantiationException, IllegalAccessException
    {
        final OperatorRuntimeSchemaBuilder builder = new OperatorRuntimeSchemaBuilder( 0, 1 );
        builder.addOutputField( 0, "count", Integer.class );

        final int tupleCount = 10;
        final int maxInt = 100;
        final OperatorConfig config = new OperatorConfig();
        final Consumer<Tuple> populator = tuple -> tuple.set( "count", random.nextInt( maxInt ) );
        config.set( TUPLE_POPULATOR_CONFIG_PARAMETER, populator );
        config.set( TUPLE_COUNT_CONFIG_PARAMETER, tupleCount );

        final OperatorDef operatorDef = OperatorDefBuilder.newInstance( "beacon", BeaconOperator.class )
                                                          .setExtendingSchema( builder )
                                                          .setConfig( config )
                                                          .build();

        final InitializationContextImpl initContext = new InitializationContextImpl( operatorDef, new boolean[] {} );

        final BeaconOperator operator = (BeaconOperator) operatorDef.createOperator();
        operator.init( initContext );

        final TuplesImpl output = new TuplesImpl( 1 );
        final InvocationContextImpl invocationContext = new InvocationContextImpl();
        invocationContext.setInvocationParameters( SUCCESS, null, output, null );

        operator.invoke( invocationContext );

        assertThat( output.getNonEmptyPortCount(), equalTo( 1 ) );

        final List<Tuple> tuples = output.getTuplesByDefaultPort();
        assertThat( tuples, hasSize( tupleCount ) );
        for ( Tuple tuple : tuples )
        {
            assertThat( maxInt, greaterThan( tuple.getInteger( "count" ) ) );
        }
    }

}

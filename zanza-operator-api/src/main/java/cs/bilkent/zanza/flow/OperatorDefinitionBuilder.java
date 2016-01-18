package cs.bilkent.zanza.flow;


import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static cs.bilkent.zanza.flow.Port.DYNAMIC_PORT_COUNT;
import cs.bilkent.zanza.operator.Operator;
import cs.bilkent.zanza.operator.OperatorConfig;
import cs.bilkent.zanza.operator.schema.annotation.OperatorSchema;
import cs.bilkent.zanza.operator.schema.annotation.PortSchema;
import cs.bilkent.zanza.operator.schema.annotation.SchemaField;
import cs.bilkent.zanza.operator.schema.runtime.OperatorRuntimeSchema;
import cs.bilkent.zanza.operator.schema.runtime.PortRuntimeSchema;
import cs.bilkent.zanza.operator.schema.runtime.RuntimeSchemaField;
import cs.bilkent.zanza.operator.spec.OperatorSpec;
import cs.bilkent.zanza.operator.spec.OperatorType;
import static cs.bilkent.zanza.operator.spec.OperatorType.PARTITIONED_STATEFUL;
import static java.util.stream.Collectors.toList;


public class OperatorDefinitionBuilder
{

    public static OperatorDefinitionBuilder newInstance ( final String id, final Class<? extends Operator> clazz )
    {
        failIfEmptyOperatorId( id );
        checkArgument( clazz != null, "clazz must be provided" );

        final OperatorSpec spec = getOperatorSpecOrFail( clazz );
        final OperatorSchema schema = getOperatorSchema( clazz );
        if ( schema != null )
        {
            checkArgument( spec.inputPortCount() != DYNAMIC_PORT_COUNT );
            checkArgument( spec.outputPortCount() != DYNAMIC_PORT_COUNT );
            checkArgument( spec.inputPortCount() >= schema.inputs().length );
            checkArgument( spec.outputPortCount() >= schema.outputs().length );
            failIfOperatorSchemaHasDuplicatePortIndices( schema );
            failIfInvalidPortIndexOnPortSchemas( spec.inputPortCount(), schema.inputs() );
            failIfInvalidPortIndexOnPortSchemas( spec.outputPortCount(), schema.outputs() );
        }

        return new OperatorDefinitionBuilder( id, clazz, spec, schema );
    }

    private static OperatorSpec getOperatorSpecOrFail ( Class<? extends Operator> clazz )
    {
        final OperatorSpec[] annotations = clazz.getDeclaredAnnotationsByType( OperatorSpec.class );
        final String errorMessage = clazz + " Operator class must have " + OperatorSpec.class.getSimpleName() + " annotation!";
        checkArgument( annotations.length == 1, errorMessage );
        return annotations[ 0 ];
    }

    private static OperatorSchema getOperatorSchema ( Class<? extends Operator> clazz )
    {
        final OperatorSchema[] annotations = clazz.getDeclaredAnnotationsByType( OperatorSchema.class );
        final String errorMessage = clazz + " Operator class can have at most 1 " + OperatorSchema.class.getSimpleName() + " annotation!";
        checkArgument( annotations.length <= 1, errorMessage );
        return annotations.length > 0 ? annotations[ 0 ] : null;
    }

    private static void failIfOperatorSchemaHasDuplicatePortIndices ( final OperatorSchema schema )
    {
        if ( schema != null )
        {
            checkArgument( schema.inputs().length == getPortIndexCount( schema.inputs() ) );
            checkArgument( schema.outputs().length == getPortIndexCount( schema.outputs() ) );
        }
    }

    private static void failIfInvalidPortIndexOnPortSchemas ( final int portCount, final PortSchema[] portSchemas )
    {
        for ( PortSchema portSchema : portSchemas )
        {
            checkArgument( portSchema.portIndex() >= 0 && portSchema.portIndex() < portCount );
        }
    }

    private static int getPortIndexCount ( final PortSchema[] portSchemas )
    {
        return (int) Arrays.stream( portSchemas ).map( PortSchema::portIndex ).distinct().count();
    }


    private final String id;

    private final Class<? extends Operator> clazz;

    private final OperatorType type;

    private int inputPortCount;

    private int outputPortCount;

    private final OperatorSchema schema;

    private OperatorRuntimeSchema extendingSchema;

    private OperatorConfig config;

    private List<String> partitionFieldNames;

    private OperatorDefinitionBuilder ( final String id,
                                        final Class<? extends Operator> clazz,
                                        final OperatorSpec spec,
                                        final OperatorSchema schema )
    {
        failIfNegativePortCount( inputPortCount, "input" );
        failIfNegativePortCount( outputPortCount, "output" );
        this.id = id;
        this.clazz = clazz;
        this.type = spec.type();
        this.inputPortCount = spec.inputPortCount();
        this.outputPortCount = spec.outputPortCount();
        this.schema = schema;
    }

    public OperatorDefinitionBuilder setInputPortCount ( final int inputPortCount )
    {
        checkState( this.inputPortCount == DYNAMIC_PORT_COUNT, "input port count can be set only once" );
        this.inputPortCount = inputPortCount;
        return this;
    }

    public OperatorDefinitionBuilder setOutputPortCount ( final int outputPortCount )
    {
        checkState( this.outputPortCount == DYNAMIC_PORT_COUNT, "output port count can be set only once" );
        this.outputPortCount = outputPortCount;
        return this;
    }

    public OperatorDefinitionBuilder setConfig ( final OperatorConfig config )
    {
        checkState( this.config == null, "config can be set only once" );
        this.config = config;
        return this;
    }

    public OperatorDefinitionBuilder setExtendingSchema ( final OperatorRuntimeSchemaBuilder extendingSchemaBuilder )
    {
        checkArgument( extendingSchemaBuilder != null, "extending schema builder can not be null" );
        return setExtendingSchema( extendingSchemaBuilder.build() );
    }

    public OperatorDefinitionBuilder setExtendingSchema ( final OperatorRuntimeSchema extendingSchema )
    {
        checkArgument( extendingSchema != null, "extending schema argument can not be null" );
        checkState( this.extendingSchema == null, "extending schema can be set only once" );
        failIfPortSchemaSizesMismatch( extendingSchema.getInputSchemas().size(), this.inputPortCount );
        failIfPortSchemaSizesMismatch( extendingSchema.getOutputSchemas().size(), this.outputPortCount );
        if ( this.schema != null )
        {
            extendingSchema.getInputSchemas()
                           .stream()
                           .forEach( s -> failIfExtendingSchemaContainsDuplicateField( s, this.schema.inputs() ) );
            extendingSchema.getOutputSchemas()
                           .stream()
                           .forEach( s -> failIfExtendingSchemaContainsDuplicateField( s, this.schema.outputs() ) );
        }
        this.extendingSchema = extendingSchema;
        return this;
    }


    public OperatorDefinitionBuilder setPartitionFieldNames ( final List<String> partitionFieldNames )
    {
        checkArgument( partitionFieldNames != null, "partition field names must be non-null" );
        checkState( ( this.type == PARTITIONED_STATEFUL && !partitionFieldNames.isEmpty() ) || ( this.type != PARTITIONED_STATEFUL
                                                                                                 && partitionFieldNames.isEmpty() ),
                    "partition field names can be only used with " + PARTITIONED_STATEFUL + " operators!" );
        checkState( this.partitionFieldNames == null, "partition key extractor can be set only once" );
        partitionFieldNames.stream().forEach( this::failIfFieldNotExistInInputPortsOrExistWithDifferentTypes );
        this.partitionFieldNames = partitionFieldNames;
        return this;
    }

    public OperatorDefinition build ()
    {
        checkState( inputPortCount != DYNAMIC_PORT_COUNT, "input port count must be set" );
        checkState( outputPortCount != DYNAMIC_PORT_COUNT, "output port count must be set" );
        checkState( !( type == PARTITIONED_STATEFUL && ( partitionFieldNames == null || partitionFieldNames.isEmpty() ) ) );
        return new OperatorDefinition( id,
                                       clazz,
                                       type,
                                       inputPortCount,
                                       outputPortCount,
                                       buildOperatorRuntimeSchema(),
                                       getConfigOrEmptyConfig(),
                                       partitionFieldNames );
    }

    private void failIfPortSchemaSizesMismatch ( final int schemaSize, final int portCount )
    {
        checkState( portCount != DYNAMIC_PORT_COUNT );
        checkArgument( schemaSize <= portCount );
    }

    private void failIfExtendingSchemaContainsDuplicateField ( final PortRuntimeSchema runtimeSchema, final PortSchema[] portSchemas )
    {
        if ( portSchemas != null )
        {
            final List<String> runtimeSchemaFieldNames = runtimeSchema.getFields().stream().map( field -> field.name ).collect( toList() );
            final boolean duplicate = Arrays.stream( portSchemas )
                                            .filter( portSchema -> portSchema.portIndex() == runtimeSchema.getPortIndex() )
                                            .flatMap( portSchema -> Arrays.stream( portSchema.fields() ).map( SchemaField::name ) )
                                            .anyMatch( runtimeSchemaFieldNames::contains );
            checkArgument( !duplicate );
        }
    }


    private void failIfFieldNotExistInInputPortsOrExistWithDifferentTypes ( final String fieldName )
    {
        Class<?> type = null;
        for ( int i = 0; i < inputPortCount; i++ )
        {
            final PortRuntimeSchema portRuntimeSchema = extendingSchema != null ? extendingSchema.getInputSchema( i ) : null;
            if ( portRuntimeSchema != null )
            {
                final RuntimeSchemaField field = portRuntimeSchema.getField( fieldName );
                if ( field != null )
                {
                    if ( type == null )
                    {
                        type = field.type;
                    }
                    else
                    {
                        checkArgument( type.equals( field.type ) );
                    }
                }
                else
                {
                    final SchemaField schemaField = getFieldFromInputPortSchemaOrFail( i, fieldName );
                    if ( type == null )
                    {
                        type = schemaField.type();
                    }
                    else
                    {
                        checkArgument( type.equals( schemaField.type() ) );
                    }
                }
            }
            else
            {
                final SchemaField schemaField = getFieldFromInputPortSchemaOrFail( i, fieldName );
                if ( type == null )
                {
                    type = schemaField.type();
                }
                else
                {
                    checkArgument( type.equals( schemaField.type() ) );
                }
            }
        }
    }

    private SchemaField getFieldFromInputPortSchemaOrFail ( final int portIndex, final String fieldName )
    {
        checkArgument( schema != null );
        for ( PortSchema portSchema : schema.inputs() )
        {
            if ( portSchema.portIndex() == portIndex )
            {
                for ( SchemaField field : portSchema.fields() )
                {
                    if ( field.name().equals( fieldName ) )
                    {
                        return field;
                    }
                }
            }
        }

        throw new IllegalArgumentException();
    }

    private OperatorConfig getConfigOrEmptyConfig ()
    {
        return config != null ? config : new OperatorConfig();
    }

    private static void failIfEmptyOperatorId ( final String operatorId )
    {
        checkArgument( !isNullOrEmpty( operatorId ), "operator id must be non-empty!" );
    }

    private void failIfNegativePortCount ( final int portCount, final String portType )
    {
        checkArgument( portCount >= 0, portType + " port count must be non-negative!" );
    }

    private OperatorRuntimeSchema buildOperatorRuntimeSchema ()
    {
        final OperatorRuntimeSchemaBuilder schemaBuilder = new OperatorRuntimeSchemaBuilder( inputPortCount, outputPortCount, schema );

        if ( extendingSchema != null )
        {
            addToSchema( extendingSchema.getInputSchemas(), schemaBuilder::getInputPortSchemaBuilder );
            addToSchema( extendingSchema.getOutputSchemas(), schemaBuilder::getOutputPortSchemaBuilder );
        }

        return schemaBuilder.build();
    }

    private void addToSchema ( final List<PortRuntimeSchema> portSchemas,
                               final Function<Integer, PortRuntimeSchemaBuilder> portSchemaBuilderProvider )
    {
        for ( PortRuntimeSchema portSchema : portSchemas )
        {
            final PortRuntimeSchemaBuilder portSchemaBuilder = portSchemaBuilderProvider.apply( portSchema.getPortIndex() );
            for ( RuntimeSchemaField schemaField : portSchema.getFields() )
            {
                portSchemaBuilder.addField( schemaField.name, schemaField.type );
            }
        }
    }

}

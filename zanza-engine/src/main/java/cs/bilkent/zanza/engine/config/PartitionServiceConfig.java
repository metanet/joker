package cs.bilkent.zanza.engine.config;

import com.typesafe.config.Config;

public class PartitionServiceConfig
{

    public static final String CONFIG_NAME = "partitionService";

    public static final String PARTITION_COUNT = "partitionCount";


    public final int partitionCount;

    public PartitionServiceConfig ( Config parentConfig )
    {
        final Config config = parentConfig.getConfig( CONFIG_NAME );
        partitionCount = config.getInt( PARTITION_COUNT );
    }

}
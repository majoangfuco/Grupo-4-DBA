package com.ecommerceb2b.backend.Config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Capa de conexión a MongoDB (Lab 3).
 *
 * Expone dos beans reutilizables por toda la aplicación:
 * <ul>
 * <li>{@link MongoClient}: pool de conexiones. Es thread-safe y caro de
 * construir, por lo que debe existir UNO solo por proceso.</li>
 * <li>{@link MongoDatabase}: handle a la base del proyecto, ya configurado
 * con el registro de códecs POJO.</li>
 * </ul>
 *
 * La URI apunta a los dos miembros del replica set. Las lecturas y
 * escrituras se configuran en "majority" porque las transacciones
 * multi-documento del checkout (punto 3 del laboratorio) lo requieren para
 * garantizar durabilidad ante un failover.
 */
@Configuration
public class MongoConfig {

    @Value("${mongo.uri}")
    private String mongoUri;

    @Value("${mongo.database:b2b}")
    private String mongoDatabase;

    @Value("${mongo.app-name:b2b-backend}")
    private String applicationName;

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient() {
        // Registro de códecs: BSON por defecto + mapeo automático de POJOs
        // (automatic = true permite serializar clases sin anotaciones).
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(
                        PojoCodecProvider.builder().automatic(true).build()));

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                .applicationName(applicationName)
                .codecRegistry(codecRegistry)
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.MAJORITY)
                .applyToClusterSettings(builder -> builder
                        // Si el replica set no está disponible se falla rápido
                        // en vez de dejar la request colgada 30 segundos.
                        .serverSelectionTimeout(10, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(10, TimeUnit.SECONDS))
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabase mongoDatabase(MongoClient mongoClient) {
        return mongoClient.getDatabase(mongoDatabase);
    }
}

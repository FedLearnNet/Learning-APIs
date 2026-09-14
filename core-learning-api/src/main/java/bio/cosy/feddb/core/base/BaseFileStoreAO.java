package bio.cosy.feddb.core.base;

import io.quarkus.arc.InjectableInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;

import static jakarta.transaction.Transactional.TxType.MANDATORY;

@ApplicationScoped
public class BaseFileStoreAO {

    private static final int LARGE_OBJECT_BUFFER_SIZE = 1024 * 1024;

    @Inject
    @Any
    InjectableInstance<DataSource> dataSource;

    /**
     * Calls storeFile with a FileInputStream created from the given file.
     * Returns the OID of the newly created large object.
     */
    public Long storeFile(File file) {
        try (InputStream targetStream = new FileInputStream(file)) {
            return storeFile(targetStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file", e);
        }
    }

    /**
     * Streams the given input into a new large object and returns its OID.
     *
     * @param inputStream the source of the file’s bytes (must not be null)
     * @return the OID of the newly created large object
     * @throws IllegalArgumentException if inputStream is null
     * @throws IllegalStateException    if an error occurs while writing to the database
     */
    @Transactional(MANDATORY)
    public Long storeFile(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }

        DataSource ds = dataSource.getActive();
        if (ds == null) {
            throw new IllegalStateException("No DataSource available for storing large object");
        }
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false); // required for large objects
            LargeObjectManager lobj = conn.unwrap(PGConnection.class).getLargeObjectAPI();
            long oid = lobj.createLO();
            LargeObject obj = lobj.open(oid, LargeObjectManager.WRITE);

            byte[] buffer = new byte[LARGE_OBJECT_BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                obj.write(buffer, 0, read);
            }
            obj.close();
            return oid;
        } catch (SQLException | IOException ex) {
            // Wrap checked exceptions in an unchecked exception so callers see a clear failure
            throw new IllegalStateException("Failed to store file into PostgreSQL large object", ex);
        }
    }

    public void deleteStoredFile(Long objectId) {
        if (objectId == null) {
            return;
        }

        DataSource ds = dataSource.getActive();
        if (ds == null) {
            throw new IllegalStateException("No DataSource available for deleting connector file");
        }

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            LargeObjectManager lobj = conn.unwrap(PGConnection.class).getLargeObjectAPI();
            lobj.delete(objectId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete connector file from PostgreSQL large object", ex);
        }
    }

    /**
     * Reads the large object identified by the given OID and writes it to a temporary file.
     *
     * @param objectId the OID of the large object to read
     * @return a File containing the object’s data
     * @throws IllegalArgumentException if objectId is null
     * @throws IllegalStateException    if the object cannot be read
     */
    @Transactional(MANDATORY)
    public File loadFile(Long objectId) {
        if (objectId == null) {
            throw new IllegalArgumentException("Object ID must not be null");
        }

        DataSource ds = dataSource.getActive();
        if (ds == null) {
            throw new IllegalStateException("No DataSource available for storing large object");
        }

        try (Connection conn = ds.getConnection()) {
            LargeObjectManager lobj = conn.unwrap(PGConnection.class).getLargeObjectAPI();
            LargeObject obj = lobj.open(objectId, LargeObjectManager.READ);
            conn.setAutoCommit(false); // required for large objects

            // Create a temporary file in the default temp directory; callers can move it as needed
            File tempFile = File.createTempFile("download-", ".bin");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[LARGE_OBJECT_BUFFER_SIZE];
                int read;
                while ((read = obj.read(buffer, 0, buffer.length)) > 0) {
                    fos.write(buffer, 0, read);
                }
            }
            obj.close();
            return tempFile;
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("Failed to load file from PostgreSQL large object", ex);
        }
    }

    @Transactional(MANDATORY)
    public String loadFileAsString(Long objectId) {
        if (objectId == null) {
            throw new IllegalArgumentException("Object ID must not be null");
        }

        DataSource ds = dataSource.getActive();
        if (ds == null) {
            throw new IllegalStateException("No DataSource available for loading large object");
        }

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false); // required for large objects
            LargeObjectManager lobj = conn.unwrap(PGConnection.class).getLargeObjectAPI();
            LargeObject obj = lobj.open(objectId, LargeObjectManager.READ);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = obj.read(buffer, 0, buffer.length)) > 0) {
                baos.write(buffer, 0, read);
            }
            obj.close();
            return baos.toString("UTF-8");
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("Failed to load file as string from PostgreSQL large object", ex);
        }
    }

    @Transactional(MANDATORY)
    public String loadFileAsBase64(Long objectId) {
        if (objectId == null) {
            throw new IllegalArgumentException("Object ID must not be null");
        }

        DataSource ds = dataSource.getActive();
        if (ds == null) {
            throw new IllegalStateException("No DataSource available for loading large object");
        }

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            LargeObjectManager lobj = conn.unwrap(PGConnection.class).getLargeObjectAPI();
            LargeObject obj = lobj.open(objectId, LargeObjectManager.READ);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = obj.read(buffer, 0, buffer.length)) > 0) {
                baos.write(buffer, 0, read);
            }
            obj.close();

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load file as Base64 from PostgreSQL large object", ex);
        }
    }
}

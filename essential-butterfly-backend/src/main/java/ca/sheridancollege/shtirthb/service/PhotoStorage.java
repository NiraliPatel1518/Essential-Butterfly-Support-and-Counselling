package ca.sheridancollege.shtirthb.service;

/*
 * Where the Memory Wall picture files are kept.
 *
 * The rest of the application only talks to this interface, so changing from
 * a folder on the server to an AWS S3 bucket means adding one class that
 * implements it (see docs/operations/memory-wall-s3.md); no service,
 * controller, table or screen changes.
 *
 * A key is a short generated name such as "memory-wall/3f2c….jpg". It is the
 * only thing the database remembers about a file.
 */
public interface PhotoStorage {

    // Saves the file. Fails with a friendly ServiceUnavailableException.
    void store(String key, byte[] content, String contentType);

    // The file's bytes. Fails with ResourceNotFoundException if it is gone.
    byte[] load(String key);

    // Removes the file. A file that is already gone is not an error, and a
    // failure is logged instead of thrown (the database record is the truth).
    void delete(String key);
}

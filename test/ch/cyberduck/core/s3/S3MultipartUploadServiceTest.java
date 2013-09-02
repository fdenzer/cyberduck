package ch.cyberduck.core.s3;

import ch.cyberduck.core.*;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.io.AbstractStreamListener;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.FinderLocal;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @version $Id$
 */
public class S3MultipartUploadServiceTest extends AbstractTestCase {

    @Test
    public void testUploadSinglePart() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                properties.getProperty("s3.key"), properties.getProperty("s3.secret")
                        )));
        session.open(new DefaultHostKeyController());
        session.login(new DisabledPasswordStore(), new DisabledLoginController());
        final S3MultipartUploadService m = new S3MultipartUploadService(session, 5 * 1024L);
        final Path container = new Path("test.cyberduck.ch", Path.VOLUME_TYPE);
        final Path test = new Path(container, UUID.randomUUID().toString(), Path.FILE_TYPE);
        test.setLocal(new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()));
        final String random = RandomStringUtils.random(1000);
        IOUtils.write(random, test.getLocal().getOutputStream(false));
        final TransferStatus status = new TransferStatus();
        status.setLength((long) random.getBytes().length);
        Preferences.instance().setProperty("s3.storage.class", "REDUCED_REDUNDANCY");
        m.upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener(), status);
        assertTrue(new S3FindFeature(session).find(test));
        final PathAttributes attributes = session.list(container,
                new DisabledListProgressListener()).get(test.getReference()).attributes();
        assertEquals(random.getBytes().length, attributes.getSize());
        assertEquals("REDUCED_REDUNDANCY", attributes.getStorageClass());
        new S3DefaultDeleteFeature(session).delete(Collections.<Path>singletonList(test), new DisabledLoginController());
        session.close();
    }

    @Test(expected = NotfoundException.class)
    public void testUploadInvalidContainer() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                properties.getProperty("s3.key"), properties.getProperty("s3.secret")
                        )));
        session.open(new DefaultHostKeyController());
        session.login(new DisabledPasswordStore(), new DisabledLoginController());
        final S3MultipartUploadService m = new S3MultipartUploadService(session, 5 * 1024L);
        final Path container = new Path("nosuchcontainer.cyberduck.ch", Path.VOLUME_TYPE);
        final Path test = new Path(container, UUID.randomUUID().toString(), Path.FILE_TYPE);
        final TransferStatus status = new TransferStatus();
        m.upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener(), status);
    }

    @Test
    public void testMultipleParts() throws Exception {
        // 5L * 1024L * 1024L
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                properties.getProperty("s3.key"), properties.getProperty("s3.secret")
                        )));
        session.open(new DefaultHostKeyController());
        session.login(new DisabledPasswordStore(), new DisabledLoginController());
        final S3MultipartUploadService m = new S3MultipartUploadService(session, 5242880L);
        final Path container = new Path("test.cyberduck.ch", Path.VOLUME_TYPE);
        final Path test = new Path(container, UUID.randomUUID().toString(), Path.FILE_TYPE);
        test.setLocal(new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()));
        final byte[] random = new byte[5242881];
        new Random().nextBytes(random);
        IOUtils.write(random, test.getLocal().getOutputStream(false));
        final TransferStatus status = new TransferStatus();
        status.setLength(random.length);
        m.upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener(), status);
        assertTrue(new S3FindFeature(session).find(test));
        assertEquals(random.length, session.list(container,
                new DisabledListProgressListener()).get(test.getReference()).attributes().getSize());
        new S3DefaultDeleteFeature(session).delete(Collections.<Path>singletonList(test), new DisabledLoginController());
        session.close();
    }

    @Test
    public void testAppendSecondPart() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                properties.getProperty("s3.key"), properties.getProperty("s3.secret")
                        )));
        session.open(new DefaultHostKeyController());
        session.login(new DisabledPasswordStore(), new DisabledLoginController());
        final Path container = new Path("test.cyberduck.ch", Path.VOLUME_TYPE);
        final Path test = new Path(container, UUID.randomUUID().toString(), Path.FILE_TYPE);
        test.setLocal(new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()));
        final byte[] random = new byte[10485760];
        new Random().nextBytes(random);
        IOUtils.write(random, test.getLocal().getOutputStream(false));
        final TransferStatus status = new TransferStatus();
        status.setLength(random.length);
        try {
            new S3MultipartUploadService(session, 10485760L).upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener() {
                long count;

                @Override
                public void bytesSent(final long bytes) {
                    if(count >= 5242880) {
                        throw new RuntimeException();
                    }
                    count += bytes;
                }
            }, status);
        }
        catch(BackgroundException e) {
            // Expected
        }
        status.setAppend(true);
        assertTrue(new S3FindFeature(session).find(test));
        assertEquals(0L, session.list(container,
                new DisabledListProgressListener()).get(test.getReference()).attributes().getSize());
        new S3MultipartUploadService(session, 10485760L).upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener(), status);
        assertTrue(new S3FindFeature(session).find(test));
        assertEquals(random.length, session.list(container,
                new DisabledListProgressListener()).get(test.getReference()).attributes().getSize());
        final byte[] buffer = new byte[random.length];
        final InputStream in = new S3ReadFeature(session).read(test, new TransferStatus());
        IOUtils.readFully(in, buffer);
        IOUtils.closeQuietly(in);
        assertArrayEquals(random, buffer);
        new S3DefaultDeleteFeature(session).delete(Collections.<Path>singletonList(test), new DisabledLoginController());
        session.close();
    }

    @Test
    public void testAppendNoPartCompleted() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                properties.getProperty("s3.key"), properties.getProperty("s3.secret")
                        )));
        session.open(new DefaultHostKeyController());
        session.login(new DisabledPasswordStore(), new DisabledLoginController());
        final Path container = new Path("test.cyberduck.ch", Path.VOLUME_TYPE);
        final Path test = new Path(container, UUID.randomUUID().toString(), Path.FILE_TYPE);
        test.setLocal(new FinderLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()));
        final byte[] random = new byte[32769];
        new Random().nextBytes(random);
        IOUtils.write(random, test.getLocal().getOutputStream(false));
        final TransferStatus status = new TransferStatus();
        status.setLength(random.length);
        try {
            new S3MultipartUploadService(session, 10485760L).upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener() {
                long count;

                @Override
                public void bytesSent(final long bytes) {
                    if(count >= 32768) {
                        throw new RuntimeException();
                    }
                    count += bytes;
                }
            }, status);
        }
        catch(BackgroundException e) {
            // Expected
        }
        status.setAppend(true);
        new S3MultipartUploadService(session, 10485760L).upload(test, new BandwidthThrottle(BandwidthThrottle.UNLIMITED), new AbstractStreamListener(), status);
        assertTrue(new S3FindFeature(session).find(test));
        assertEquals(random.length, session.list(container,
                new DisabledListProgressListener()).get(test.getReference()).attributes().getSize());
        final byte[] buffer = new byte[random.length];
        final InputStream in = new S3ReadFeature(session).read(test, new TransferStatus());
        IOUtils.readFully(in, buffer);
        IOUtils.closeQuietly(in);
        assertArrayEquals(random, buffer);
        new S3DefaultDeleteFeature(session).delete(Collections.<Path>singletonList(test), new DisabledLoginController());
        session.close();
    }
}

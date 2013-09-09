package ch.cyberduck.core.transfer.upload;

import ch.cyberduck.core.*;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.symlink.NullSymlinkResolver;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @version $Id$
 */
public class OverwriteFilterTest extends AbstractTestCase {

    @Test
    public void testAcceptNoLocal() throws Exception {
        final OverwriteFilter f = new OverwriteFilter(new NullSymlinkResolver(), new NullSession(new Host("h")));
        // Local file does not exist
        assertFalse(f.accept(new Path("a", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
        }, new TransferStatus()));
        assertFalse(f.accept(new Path("a", Path.DIRECTORY_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t") {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
        }, new TransferStatus()));
    }

    @Test
    public void testAcceptRemoteExists() throws Exception {
        final OverwriteFilter f = new OverwriteFilter(new NullSymlinkResolver(), new NullSession(new Host("h")));
        assertTrue(f.accept(new Path("a", Path.DIRECTORY_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t");
            }
        }, new TransferStatus()));
        assertTrue(f.accept(new Path("a", Path.DIRECTORY_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "t");
            }
        }, new TransferStatus()
        ));
    }

    @Test
    public void testSize() throws Exception {
        final OverwriteFilter f = new OverwriteFilter(new NullSymlinkResolver(), new NullSession(new Host("h")));
        assertEquals(1L, f.prepare(new Path("/t", Path.FILE_TYPE) {
            @Override
            public Local getLocal() {
                return new NullLocal(null, "/t") {
                    @Override
                    public Attributes attributes() {
                        return new PathAttributes(Path.FILE_TYPE) {
                            @Override
                            public long getSize() {
                                return 1L;
                            }
                        };
                    }
                };
            }
        }, new ch.cyberduck.core.transfer.TransferStatus()).getLength(), 0L);
    }

    @Test
    public void testPermissionsNoChange() throws Exception {
        final OverwriteFilter f = new OverwriteFilter(new NullSymlinkResolver(), new NullSession(new Host("h")));
        final Path file = new Path("/t", Path.FILE_TYPE);
        file.setLocal(new NullLocal(null, "a"));
        assertFalse(f.prepare(file, new ch.cyberduck.core.transfer.TransferStatus()).isComplete());
        assertEquals(Acl.EMPTY, file.attributes().getAcl());
        assertEquals(Permission.EMPTY, file.attributes().getPermission());
    }

    @Test
    public void testPermissionsExistsNoChange() throws Exception {
        final OverwriteFilter f = new OverwriteFilter(new NullSymlinkResolver(), new NullSession(new Host("h")));
        final Path file = new Path("/t", Path.FILE_TYPE);
        file.setLocal(new NullLocal(null, "a"));
        assertFalse(f.prepare(file, new ch.cyberduck.core.transfer.TransferStatus()).isComplete());
        assertEquals(Acl.EMPTY, file.attributes().getAcl());
        assertEquals(Permission.EMPTY, file.attributes().getPermission());
    }
}
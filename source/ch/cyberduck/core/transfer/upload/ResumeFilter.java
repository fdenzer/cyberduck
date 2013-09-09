package ch.cyberduck.core.transfer.upload;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.shared.DefaultFileSizeFeature;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.symlink.SymlinkResolver;

/**
 * @version $Id$
 */
public class ResumeFilter extends AbstractUploadFilter {

    public ResumeFilter(final SymlinkResolver symlinkResolver) {
        super(symlinkResolver);
    }

    @Override
    public TransferStatus prepare(final Session<?> session, final Path file, final TransferStatus parent) throws BackgroundException {
        final TransferStatus status = super.prepare(session, file, parent);
        if(file.attributes().isFile()) {
            if(parent.isExists()) {
                final Write write = session.getFeature(Write.class);
                final Write.Append append = write.append(file, new DefaultFileSizeFeature(session));
                if(append.append) {
                    // Append to existing file
                    status.setAppend(true);
                    status.setCurrent(append.size);
                }
            }
        }
        return status;
    }
}
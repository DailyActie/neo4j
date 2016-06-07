/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.security.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.neo4j.string.HexString;
import org.neo4j.string.UTF8;

import static java.lang.String.format;

/**
 * Serializes user authorization and authentication data to a format similar to unix passwd files.
 */
public class UserSerialization
{
    public class FormatException extends Exception
    {
        FormatException( String message )
        {
            super( message );
        }
    }

    private static final String userSeparator = ":";
    private static final String credentialSeparator = ",";

    public byte[] serialize(Collection<User> users)
    {
        StringBuilder sb = new StringBuilder();
        for ( User user : users )
        {
            sb.append( serialize(user) ).append( "\n" );
        }
        return UTF8.encode( sb.toString() );
    }

    public List<User> deserializeUsers( byte[] bytes ) throws FormatException
    {
        List<User> out = new ArrayList<>();
        int lineNumber = 1;
        for ( String line : UTF8.decode( bytes ).split( "\n" ) )
        {
            if (line.trim().length() > 0)
            {
                out.add( deserializeUser( line, lineNumber ) );
            }
            lineNumber++;
        }
        return out;
    }

    private String serialize( User user )
    {
        return join( userSeparator, user.name(),
                serialize( user.credentials() ),
                join( ",", user.getFlags() )
            );
    }

    private User deserializeUser( String line, int lineNumber ) throws FormatException
    {
        String[] parts = line.split( userSeparator, -1 );
        if ( parts.length != 3 )
        {
            throw new FormatException( format( "wrong number of line fields, expected 3, got %d [line %d]",
                    parts.length,
                    lineNumber
            ) );
        }

        User.Builder b = new User.Builder()
                .withName( parts[0] )
                .withCredentials( deserializeCredentials( parts[1], lineNumber ) );

        for ( String flag : parts[2].split( ",", -1 ))
        {
            String trimmed = flag.trim();
            if (!trimmed.isEmpty())
                b = b.withFlag( trimmed );
        }

        return  b.build();
    }

    private String serialize( Credential cred )
    {
        String encodedSalt = HexString.encodeHexString( cred.salt() );
        String encodedPassword = HexString.encodeHexString( cred.passwordHash() );
        return join( credentialSeparator, Credential.DIGEST_ALGO, encodedPassword, encodedSalt );
    }

    private Credential deserializeCredentials( String part, int lineNumber ) throws FormatException
    {
        String[] split = part.split( credentialSeparator, -1 );
        if ( split.length != 3 )
        {
            throw new FormatException( format( "wrong number of credential fields [line %d]", lineNumber ) );
        }
        if ( !split[0].equals( Credential.DIGEST_ALGO ) )
        {
            throw new FormatException( format( "unknown digest \"%s\" [line %d]", split[0], lineNumber ) );
        }
        byte[] decodedPassword = HexString.decodeHexString( split[1] );
        byte[] decodedSalt = HexString.decodeHexString( split[2] );
        return new Credential( decodedSalt, decodedPassword );
    }

    private String join( String separator, String... segments )
    {
        return join(separator, Arrays.asList(segments).iterator() );
    }

    private String join( String separator, Iterator<String> segments )
    {
        StringBuilder sb = new StringBuilder();
        boolean afterFirst = false;
        while (segments.hasNext())
        {
            if ( afterFirst ) { sb.append( separator ); }
            else              { afterFirst = true;      }
            sb.append( segments.next() );
        }
        return sb.toString();
    }
}

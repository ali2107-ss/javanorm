package ru.normacontrol.application.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.domain.entity.User;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-04T00:04:02+0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.roles( rolesToStrings( user.getRoles() ) );
        userResponse.createdAt( user.getCreatedAt() );
        userResponse.email( user.getEmail() );
        userResponse.enabled( user.isEnabled() );
        userResponse.fullName( user.getFullName() );
        userResponse.id( user.getId() );
        userResponse.username( user.getUsername() );

        return userResponse.build();
    }
}

package com.launchfleet.backend.users;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByEmailAndActiveTrue(String email);

	boolean existsByEmail(String email);

}

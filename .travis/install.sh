#!/usr/bin/env sh

if [ "$MANUAL_RELEASE_TRIGGERED" = "true" ];
then
	# This is a release job, triggered manually
	# Change the version locally to remove the -SNAPSHOT
	sed -i "s/-SNAPSHOT//g" gradle.properties
	echo "This is a manual release!"
else
	echo "Not a manual release"
fi

if [ -z "$encrypted_7407e5ec1bc3_key" ] || [ -z "$encrypted_7407e5ec1bc3_iv" ] || [ -z "$ENCRYPTED_GPG_KEY_LOCATION" ] || [ -z "$GPG_KEY_LOCATION" ];
then
    echo "The secret signing key is not being decrypted (the necessary environment variables are not set)."
else
    echo "Decrypting the secret signing key…"
    openssl aes-256-cbc -K $encrypted_7407e5ec1bc3_key -iv $encrypted_7407e5ec1bc3_iv -in $ENCRYPTED_GPG_KEY_LOCATION -out $GPG_KEY_LOCATION -d
fi

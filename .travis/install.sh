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

if [ "$TRAVIS_BRANCH" = "master" ] && [ "$TRAVIS_PULL_REQUEST" = "false" ];
then
    openssl aes-256-cbc -K $encrypted_7407e5ec1bc3_key -iv $encrypted_7407e5ec1bc3_iv -in $ENCRYPTED_GPG_KEY_LOCATION -out $GPG_KEY_LOCATION -d
fi

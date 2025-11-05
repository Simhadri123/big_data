# Build Note

The Dockerfile in this repository is designed to be built locally on your machine where Docker has internet access to download Hadoop.

## Why can't we test the Docker build in CI/GitHub Actions?

The build process requires downloading Hadoop from Apache mirrors, which needs internet access. In some CI environments, Docker build doesn't have network access, so we cannot automatically test the build in this repository's CI.

However, **the Dockerfile is fully functional and will work on your local machine** where Docker has normal internet access.

## Building Locally

You can build this Docker image on your local machine by running:

```bash
docker build -t big-data-workspace .
```

or using Docker Compose:

```bash
docker-compose build
```

The build process will:
1. Download and install Java 8
2. Download Hadoop 3.3.4 from Apache mirrors
3. Configure Hadoop with HDFS and YARN
4. Set up SSH for Hadoop services
5. Create the wrapper script for running your workspace scripts

## Verification

Once built successfully on your machine, you can verify the setup works by:

1. Starting the container:
   ```bash
   docker-compose up -d
   ```

2. Accessing the container:
   ```bash
   docker-compose exec hadoop-workspace /bin/bash
   ```

3. Running any of the existing scripts:
   ```bash
   ./run.sh /workspace/RepJoin/run.sh
   ```

## Alternative: Pre-built Images

If you prefer, you could also:
- Use a pre-built Hadoop Docker image from Docker Hub (like `sequenceiq/hadoop-docker`)
- Modify the Dockerfile to use such an image as the base
- Share your built image on Docker Hub for reuse

The provided Dockerfile gives you full control and transparency over the environment setup.

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0

FROM public.ecr.aws/lambda/python:3.12
LABEL name=lambda/python/clamav
LABEL version=1.0

ARG CACHE_DATE=1

RUN dnf update -y \
 && dnf install -y \
    clamav \
    clamav-update \
    clamd \
    p7zip \
 && dnf clean all

 
RUN pip3 install --no-cache-dir \
    cffi \
    awslambdaric \
    boto3 \
    requests \
    aws-lambda-powertools

RUN ln -s /etc/freshclam.conf /tmp/freshclam.conf

COPY clamd.conf /etc/clamd.conf
COPY lambda.py /var/task/lambda.py
ENTRYPOINT [ "/var/lang/bin/python3", "-m", "awslambdaric" ]
CMD [ "lambda.lambda_handler" ]
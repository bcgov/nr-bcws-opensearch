# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0

FROM public.ecr.aws/lambda/python:3.12
LABEL name=lambda/python/clamav
LABEL version=1.0

ARG CACHE_DATE=1

RUN dnf update -y \
 && dnf install -y \
    clamav1.4 \
    clamav1.4-update \
    clamd1.4 \
    p7zip \
 && dnf clean all

 
RUN pip3 install --no-cache-dir \
 --only-binary=:all: \
 cffi==2.1.0 \
 awslambdaric==4.0.1 \
 boto3==1.42.97 \
 requests==2.34.2 \
 aws-lambda-powertools==3.31.1

RUN ln -s /etc/freshclam.conf /tmp/freshclam.conf

COPY clamd.conf /etc/clamd.conf
COPY lambda.py /var/task/lambda.py
ENTRYPOINT [ "/var/lang/bin/python3", "-m", "awslambdaric" ]
CMD [ "lambda.lambda_handler" ]
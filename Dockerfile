FROM jupyter/minimal-notebook:3395de4db93a

# name your environment and choose python 3.x version
ARG conda_env=python3
ARG py_ver=3.8

# you can add additional libraries you want conda to install by listing them below the first line and ending with "&& \"
RUN conda create --quiet --yes -p $CONDA_DIR/envs/$conda_env python=$py_ver ipython ipykernel && \
    conda clean --all -f -y

# create Python 3.x environment and link it to jupyter
RUN $CONDA_DIR/envs/${conda_env}/bin/python -m ipykernel install --user --name=${conda_env} && \
    fix-permissions $CONDA_DIR && \
    fix-permissions /home/$NB_USER

# any additional pip installs can be added by uncommenting the following line
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install pandas
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install numpy
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install matplotlib
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install tqdm
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install ansible
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install boto
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install boto3
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install botocore
RUN $CONDA_DIR/envs/${conda_env}/bin/pip install pipenv


# prepend conda environment to path
ENV PATH $CONDA_DIR/envs/${conda_env}/bin:$PATH

# if you want this environment to be the default one, uncomment the following line:
ENV CONDA_DEFAULT_ENV ${conda_env}

USER root

RUN apt-get update && apt-get install -y \
  default-jdk \
  maven  \
  curl \
  openssh-client \
  dos2unix \
  awscli

# Get Rust
RUN curl https://sh.rustup.rs -sSf | bash -s -- -y
ENV PATH="/home/$NB_USER/.cargo/bin:${PATH}"

RUN ansible-galaxy collection install amazon.aws
RUN ansible-galaxy collection install community.aws

USER $NB_USER

ADD . .

USER root
# Windows compability
RUN dos2unix /home/$NB_USER/zeph-crypto/native/compile-and-install.sh

RUN mvn clean install
RUN mkdir -p /home/$NB_USER/results/review-results
RUN mkdir -p /home/$NB_USER/data
RUN mkdir -p /home/$NB_USER/logs
RUN fix-permissions "/home/$NB_USER/.cargo"
RUN fix-permissions "/home/$NB_USER/.ansible"
RUN fix-permissions "/home/$NB_USER/ansible"
RUN fix-permissions "/home/$NB_USER/results"
RUN fix-permissions "/home/$NB_USER/data"
RUN fix-permissions "/home/$NB_USER/logs"
RUN fix-permissions "/home/$NB_USER/zeph-benchmarks"
RUN fix-permissions "/home/$NB_USER/zeph-client"
RUN fix-permissions "/home/$NB_USER/zeph-crypto"
RUN fix-permissions "/home/$NB_USER/zeph-server"
RUN fix-permissions "/home/$NB_USER/zeph-shared"
RUN fix-permissions "/home/$NB_USER/results/review-results"

RUN mv /home/$NB_USER/aws-material/boto /home/$NB_USER/.boto
RUN mkdir /home/$NB_USER/.ssh
RUN mv /home/$NB_USER/aws-material/id_rsa_zeph /home/$NB_USER/.ssh/
RUN chgrp $NB_GID /home/$NB_USER/.ssh
RUN chmod g+rw /home/$NB_USER/.ssh
RUN chgrp $NB_GID /home/$NB_USER/.ssh/id_rsa_zeph
RUN chmod g+rw /home/$NB_USER/.ssh/id_rsa_zeph
RUN chgrp $NB_GID /home/$NB_USER/.boto
RUN chmod g+rwX /home/$NB_USER/.boto

RUN apt-get --purge remove -y dos2unix
USER $NB_USER

CMD ["start.sh", "jupyter", "lab"]


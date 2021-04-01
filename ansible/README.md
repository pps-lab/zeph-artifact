# Zeph Benchmarks


Running all end-to-end benchmarks for all applications:

```
ansible-playbook ansible/e2epaper.yml -i ansible/inventory --private-key ~/.ssh/id_rsa_zeph --ssh-common-args='-o StrictHostKeyChecking=no' --forks=24

``` 

Run a specific end-to-end benchmark for one application. Replace `<APPLICATION>` with one of `polar`, `car`, or `web` and replace `<N_PARTIES>` with a multiple of 300.
For more than 1200 parties, increase the forks argument accordingly (we need a fork for each each client machine).


```
ansible-playbook ansible/e2esingle.yml -i ansible/inventory -e "application=<APPLICATION> num_parties=<N_PARTIES>" --forks 24 --private-key ~/.ssh/id_rsa_zeph --ssh-common-args='-o StrictHostKeyChecking=no'

```

## Requirements

- boto credentials for AWS
- private key `~/.ssh/id_rsa_zeph`
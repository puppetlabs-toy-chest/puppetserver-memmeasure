(ns puppetserver-memmeasure.util
  (:require [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetlabs.services.jruby.jruby-puppet-internal
             :as jruby-puppet-internal]
            [puppetlabs.services.jruby.puppet-environments :as puppet-env]
            [puppetlabs.services.jruby.jruby-puppet-internal :as jruby-internal]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [puppetlabs.services.request-handler.request-handler-core
             :as request-handler-core]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [puppetlabs.kitchensink.core :as ks]
            [clojure.string :as str])
  (:import (com.yourkit.api Controller MemorySnapshot)
           (java.io File)
           (java.util HashMap)
           (com.puppetlabs.puppetserver JRubyPuppet)
           (com.puppetlabs.puppetserver.jruby ScriptingContainer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn catalog-request :- {schema/Str schema/Any}
  [node-name :- schema/Str
   environment-name :- schema/Str
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig]
  {"authenticated" false,
   "headers" {"accept" "pson, dot, binary", "x-puppet-version" "4.5.2", "user-agent" "Ruby", "host" "rll.corp.puppetlabs.net:8140", "accept-encoding" "gzip;q=1.0,deflate;q=0.6,identity;q=0.3", "content-length" "18473", "content-type" "application/x-www-form-urlencoded"}
   "remote-addr" "10.32.116.16"
   "client-cert-cn" ""
   "client-cert" nil
   "body" (str "environment="
               environment-name
               "&facts_format=pson&facts=%257B%2522name%2522%253A%2522x1pqeru6g8miy73.delivery.puppetlabs.net%2522%252C%2522values%2522%253A%257B%2522agent_specified_environment%2522%253A%2522"
               environment-name
               "%2522%252C%2522aio_agent_version%2522%253A%25221.5.2%2522%252C%2522architecture%2522%253A%2522x86_64%2522%252C%2522augeas%2522%253A%257B%2522version%2522%253A%25221.4.0%2522%257D%252C%2522augeasversion%2522%253A%25221.4.0%2522%252C%2522bios_release_date%2522%253A%252207%252F30%252F2013%2522%252C%2522bios_vendor%2522%253A%2522Phoenix%2BTechnologies%2BLTD%2522%252C%2522bios_version%2522%253A%25226.00%2522%252C%2522blockdevice_fd0_size%2522%253A4096%252C%2522blockdevice_sda_model%2522%253A%2522Virtual%2Bdisk%2522%252C%2522blockdevice_sda_size%2522%253A17179869184%252C%2522blockdevice_sda_vendor%2522%253A%2522VMware%2522%252C%2522blockdevice_sr0_model%2522%253A%2522VMware%2BIDE%2BCDR10%2522%252C%2522blockdevice_sr0_size%2522%253A1073741312%252C%2522blockdevice_sr0_vendor%2522%253A%2522NECVMWar%2522%252C%2522blockdevices%2522%253A%2522fd0%252Csda%252Csr0%2522%252C%2522boardmanufacturer%2522%253A%2522Intel%2BCorporation%2522%252C%2522boardproductname%2522%253A%2522440BX%2BDesktop%2BReference%2BPlatform%2522%252C%2522boardserialnumber%2522%253A%2522None%2522%252C%2522chassisassettag%2522%253A%2522No%2BAsset%2BTag%2522%252C%2522chassistype%2522%253A%2522Other%2522%252C%2522choco_install_path%2522%253A%2522C%253A%255C%255CProgramData%255C%255Cchocolatey%2522%252C%2522chocolateyversion%2522%253A%25220%2522%252C%2522dhcp_servers%2522%253A%257B%2522ens160%2522%253A%252210.32.22.10%2522%252C%2522system%2522%253A%252210.32.22.10%2522%257D%252C%2522disks%2522%253A%257B%2522fd0%2522%253A%257B%2522size%2522%253A%25224.00%2BKiB%2522%252C%2522size_bytes%2522%253A4096%257D%252C%2522sda%2522%253A%257B%2522model%2522%253A%2522Virtual%2Bdisk%2522%252C%2522size%2522%253A%252216.00%2BGiB%2522%252C%2522size_bytes%2522%253A17179869184%252C%2522vendor%2522%253A%2522VMware%2522%257D%252C%2522sr0%2522%253A%257B%2522model%2522%253A%2522VMware%2BIDE%2BCDR10%2522%252C%2522size%2522%253A%25221.00%2BGiB%2522%252C%2522size_bytes%2522%253A1073741312%252C%2522vendor%2522%253A%2522NECVMWar%2522%257D%257D%252C%2522dmi%2522%253A%257B%2522bios%2522%253A%257B%2522release_date%2522%253A%252207%252F30%252F2013%2522%252C%2522vendor%2522%253A%2522Phoenix%2BTechnologies%2BLTD%2522%252C%2522version%2522%253A%25226.00%2522%257D%252C%2522board%2522%253A%257B%2522manufacturer%2522%253A%2522Intel%2BCorporation%2522%252C%2522product%2522%253A%2522440BX%2BDesktop%2BReference%2BPlatform%2522%252C%2522serial_number%2522%253A%2522None%2522%257D%252C%2522chassis%2522%253A%257B%2522asset_tag%2522%253A%2522No%2BAsset%2BTag%2522%252C%2522type%2522%253A%2522Other%2522%257D%252C%2522manufacturer%2522%253A%2522VMware%252C%2BInc.%2522%252C%2522product%2522%253A%257B%2522name%2522%253A%2522VMware%2BVirtual%2BPlatform%2522%252C%2522serial_number%2522%253A%2522VMware-42%2B0f%2Bb7%2B8e%2Bad%2B1a%2B55%2Bfc-47%2B6f%2B48%2Bd2%2B98%2B51%2B31%2Ba7%2522%252C%2522uuid%2522%253A%2522420FB78E-AD1A-55FC-476F-48D2985131A7%2522%257D%257D%252C%2522domain%2522%253A%2522delivery.puppetlabs.net%2522%252C%2522facterversion%2522%253A%25223.2.0%2522%252C%2522filesystems%2522%253A%2522xfs%2522%252C%2522fqdn%2522%253A%2522x1pqeru6g8miy73.delivery.puppetlabs.net%2522%252C%2522gid%2522%253A%2522root%2522%252C%2522hardwareisa%2522%253A%2522x86_64%2522%252C%2522hardwaremodel%2522%253A%2522x86_64%2522%252C%2522hostname%2522%253A%2522x1pqeru6g8miy73%2522%252C%2522id%2522%253A%2522root%2522%252C%2522identity%2522%253A%257B%2522gid%2522%253A0%252C%2522group%2522%253A%2522root%2522%252C%2522uid%2522%253A0%252C%2522user%2522%253A%2522root%2522%257D%252C%2522interfaces%2522%253A%2522ens160%252Clo%2522%252C%2522ip6tables_version%2522%253A%25221.4.21%2522%252C%2522ipaddress%2522%253A%252210.32.116.16%2522%252C%2522ipaddress6%2522%253A%2522fe80%253A%253A250%253A56ff%253Afe8f%253A8e97%2522%252C%2522ipaddress6_ens160%2522%253A%2522fe80%253A%253A250%253A56ff%253Afe8f%253A8e97%2522%252C%2522ipaddress6_lo%2522%253A%2522%253A%253A1%2522%252C%2522ipaddress_ens160%2522%253A%252210.32.116.16%2522%252C%2522ipaddress_lo%2522%253A%2522127.0.0.1%2522%252C%2522iptables_version%2522%253A%25221.4.21%2522%252C%2522is_pe%2522%253Afalse%252C%2522is_virtual%2522%253Atrue%252C%2522java_default_home%2522%253A%2522%252Fusr%252Flib%252Fjvm%252Fjava-1.7.0-openjdk-1.7.0.101-2.6.6.1.el7_2.x86_64%2522%252C%2522java_libjvm_path%2522%253A%2522%252Fusr%252Flib%252Fjvm%252Fjava-1.7.0-openjdk-1.7.0.101-2.6.6.1.el7_2.x86_64%252Fjre%252Flib%252Famd64%252Fserver%2522%252C%2522java_major_version%2522%253A%25227%2522%252C%2522java_patch_level%2522%253A%2522101%2522%252C%2522java_version%2522%253A%25221.7.0_101%2522%252C%2522jenkins_plugins%2522%253A%2522%2522%252C%2522kernel%2522%253A%2522Linux%2522%252C%2522kernelmajversion%2522%253A%25223.10%2522%252C%2522kernelrelease%2522%253A%25223.10.0-123.4.2.el7.x86_64%2522%252C%2522kernelversion%2522%253A%25223.10.0%2522%252C%2522load_averages%2522%253A%257B%252215m%2522%253A0.05%252C%25221m%2522%253A0.09%252C%25225m%2522%253A0.04%257D%252C%2522macaddress%2522%253A%252200%253A50%253A56%253A8f%253A8e%253A97%2522%252C%2522macaddress_ens160%2522%253A%252200%253A50%253A56%253A8f%253A8e%253A97%2522%252C%2522manufacturer%2522%253A%2522VMware%252C%2BInc.%2522%252C%2522memory%2522%253A%257B%2522swap%2522%253A%257B%2522available%2522%253A%25221.60%2BGiB%2522%252C%2522available_bytes%2522%253A1719660544%252C%2522capacity%2522%253A%25220%2525%2522%252C%2522total%2522%253A%25221.60%2BGiB%2522%252C%2522total_bytes%2522%253A1719660544%252C%2522used%2522%253A%25220%2Bbytes%2522%252C%2522used_bytes%2522%253A0%257D%252C%2522system%2522%253A%257B%2522available%2522%253A%25223.30%2BGiB%2522%252C%2522available_bytes%2522%253A3541131264%252C%2522capacity%2522%253A%252210.96%2525%2522%252C%2522total%2522%253A%25223.70%2BGiB%2522%252C%2522total_bytes%2522%253A3976998912%252C%2522used%2522%253A%2522415.68%2BMiB%2522%252C%2522used_bytes%2522%253A435867648%257D%257D%252C%2522memoryfree%2522%253A%25223.30%2BGiB%2522%252C%2522memoryfree_mb%2522%253A3377.0859375%252C%2522memorysize%2522%253A%25223.70%2BGiB%2522%252C%2522memorysize_mb%2522%253A3792.76171875%252C%2522mountpoints%2522%253A%257B%2522%252F%2522%253A%257B%2522available%2522%253A%252212.24%2BGiB%2522%252C%2522available_bytes%2522%253A13137911808%252C%2522capacity%2522%253A%252211.95%2525%2522%252C%2522device%2522%253A%2522%252Fdev%252Fmapper%252Fcentos-root%2522%252C%2522filesystem%2522%253A%2522xfs%2522%252C%2522options%2522%253A%255B%2522rw%2522%252C%2522seclabel%2522%252C%2522relatime%2522%252C%2522attr2%2522%252C%2522inode64%2522%252C%2522noquota%2522%255D%252C%2522size%2522%253A%252213.90%2BGiB%2522%252C%2522size_bytes%2522%253A14921236480%252C%2522used%2522%253A%25221.66%2BGiB%2522%252C%2522used_bytes%2522%253A1783324672%257D%252C%2522%252Fboot%2522%253A%257B%2522available%2522%253A%2522371.50%2BMiB%2522%252C%2522available_bytes%2522%253A389541888%252C%2522capacity%2522%253A%252225.20%2525%2522%252C%2522device%2522%253A%2522%252Fdev%252Fsda1%2522%252C%2522filesystem%2522%253A%2522xfs%2522%252C%2522options%2522%253A%255B%2522rw%2522%252C%2522seclabel%2522%252C%2522relatime%2522%252C%2522attr2%2522%252C%2522inode64%2522%252C%2522noquota%2522%255D%252C%2522size%2522%253A%2522496.67%2BMiB%2522%252C%2522size_bytes%2522%253A520794112%252C%2522used%2522%253A%2522125.17%2BMiB%2522%252C%2522used_bytes%2522%253A131252224%257D%257D%252C%2522mtu_ens160%2522%253A1500%252C%2522mtu_lo%2522%253A65536%252C%2522netmask%2522%253A%2522255.255.240.0%2522%252C%2522netmask6%2522%253A%2522ffff%253Affff%253Affff%253Affff%253A%253A%2522%252C%2522netmask6_ens160%2522%253A%2522ffff%253Affff%253Affff%253Affff%253A%253A%2522%252C%2522netmask6_lo%2522%253A%2522ffff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%2522%252C%2522netmask_ens160%2522%253A%2522255.255.240.0%2522%252C%2522netmask_lo%2522%253A%2522255.0.0.0%2522%252C%2522network%2522%253A%252210.32.112.0%2522%252C%2522network6%2522%253A%2522fe80%253A%253A%2522%252C%2522network6_ens160%2522%253A%2522fe80%253A%253A%2522%252C%2522network6_lo%2522%253A%2522%253A%253A1%2522%252C%2522network_ens160%2522%253A%252210.32.112.0%2522%252C%2522network_lo%2522%253A%2522127.0.0.0%2522%252C%2522networking%2522%253A%257B%2522dhcp%2522%253A%252210.32.22.10%2522%252C%2522domain%2522%253A%2522delivery.puppetlabs.net%2522%252C%2522fqdn%2522%253A%2522x1pqeru6g8miy73.delivery.puppetlabs.net%2522%252C%2522hostname%2522%253A%2522x1pqeru6g8miy73%2522%252C%2522interfaces%2522%253A%257B%2522ens160%2522%253A%257B%2522bindings%2522%253A%255B%257B%2522address%2522%253A%252210.32.116.16%2522%252C%2522netmask%2522%253A%2522255.255.240.0%2522%252C%2522network%2522%253A%252210.32.112.0%2522%257D%255D%252C%2522bindings6%2522%253A%255B%257B%2522address%2522%253A%2522fe80%253A%253A250%253A56ff%253Afe8f%253A8e97%2522%252C%2522netmask%2522%253A%2522ffff%253Affff%253Affff%253Affff%253A%253A%2522%252C%2522network%2522%253A%2522fe80%253A%253A%2522%257D%255D%252C%2522dhcp%2522%253A%252210.32.22.10%2522%252C%2522ip%2522%253A%252210.32.116.16%2522%252C%2522ip6%2522%253A%2522fe80%253A%253A250%253A56ff%253Afe8f%253A8e97%2522%252C%2522mac%2522%253A%252200%253A50%253A56%253A8f%253A8e%253A97%2522%252C%2522mtu%2522%253A1500%252C%2522netmask%2522%253A%2522255.255.240.0%2522%252C%2522netmask6%2522%253A%2522ffff%253Affff%253Affff%253Affff%253A%253A%2522%252C%2522network%2522%253A%252210.32.112.0%2522%252C%2522network6%2522%253A%2522fe80%253A%253A%2522%257D%252C%2522lo%2522%253A%257B%2522bindings%2522%253A%255B%257B%2522address%2522%253A%2522127.0.0.1%2522%252C%2522netmask%2522%253A%2522255.0.0.0%2522%252C%2522network%2522%253A%2522127.0.0.0%2522%257D%255D%252C%2522bindings6%2522%253A%255B%257B%2522address%2522%253A%2522%253A%253A1%2522%252C%2522netmask%2522%253A%2522ffff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%2522%252C%2522network%2522%253A%2522%253A%253A1%2522%257D%255D%252C%2522ip%2522%253A%2522127.0.0.1%2522%252C%2522ip6%2522%253A%2522%253A%253A1%2522%252C%2522mtu%2522%253A65536%252C%2522netmask%2522%253A%2522255.0.0.0%2522%252C%2522netmask6%2522%253A%2522ffff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%253Affff%2522%252C%2522network%2522%253A%2522127.0.0.0%2522%252C%2522network6%2522%253A%2522%253A%253A1%2522%257D%257D%252C%2522ip%2522%253A%252210.32.116.16%2522%252C%2522ip6%2522%253A%2522fe80%253A%253A250%253A56ff%253Afe8f%253A8e97%2522%252C%2522mac%2522%253A%252200%253A50%253A56%253A8f%253A8e%253A97%2522%252C%2522mtu%2522%253A1500%252C%2522netmask%2522%253A%2522255.255.240.0%2522%252C%2522netmask6%2522%253A%2522ffff%253Affff%253Affff%253Affff%253A%253A%2522%252C%2522network%2522%253A%252210.32.112.0%2522%252C%2522network6%2522%253A%2522fe80%253A%253A%2522%252C%2522primary%2522%253A%2522ens160%2522%257D%252C%2522operatingsystem%2522%253A%2522CentOS%2522%252C%2522operatingsystemmajrelease%2522%253A%25227%2522%252C%2522operatingsystemrelease%2522%253A%25227.0.1406%2522%252C%2522os%2522%253A%257B%2522architecture%2522%253A%2522x86_64%2522%252C%2522family%2522%253A%2522RedHat%2522%252C%2522hardware%2522%253A%2522x86_64%2522%252C%2522name%2522%253A%2522CentOS%2522%252C%2522release%2522%253A%257B%2522full%2522%253A%25227.0.1406%2522%252C%2522major%2522%253A%25227%2522%252C%2522minor%2522%253A%25220%2522%257D%252C%2522selinux%2522%253A%257B%2522config_mode%2522%253A%2522enforcing%2522%252C%2522current_mode%2522%253A%2522enforcing%2522%252C%2522enabled%2522%253Atrue%252C%2522enforced%2522%253Atrue%252C%2522policy_version%2522%253A%252228%2522%257D%257D%252C%2522osfamily%2522%253A%2522RedHat%2522%252C%2522package_provider%2522%253A%2522yum%2522%252C%2522partitions%2522%253A%257B%2522%252Fdev%252Fmapper%252Fcentos-root%2522%253A%257B%2522filesystem%2522%253A%2522xfs%2522%252C%2522mount%2522%253A%2522%252F%2522%252C%2522size%2522%253A%252213.91%2BGiB%2522%252C%2522size_bytes%2522%253A14931722240%252C%2522uuid%2522%253A%25223e1d8d4e-2216-4563-8d58-b433c8faf8ce%2522%257D%252C%2522%252Fdev%252Fmapper%252Fcentos-swap%2522%253A%257B%2522filesystem%2522%253A%2522swap%2522%252C%2522size%2522%253A%25221.60%2BGiB%2522%252C%2522size_bytes%2522%253A1719664640%252C%2522uuid%2522%253A%252286cb57f4-9390-448b-867e-35c3ebcf8238%2522%257D%252C%2522%252Fdev%252Fsda1%2522%253A%257B%2522filesystem%2522%253A%2522xfs%2522%252C%2522mount%2522%253A%2522%252Fboot%2522%252C%2522size%2522%253A%2522500.00%2BMiB%2522%252C%2522size_bytes%2522%253A524288000%252C%2522uuid%2522%253A%25224a9725cc-739a-45f5-8ea0-a83885eaeea0%2522%257D%252C%2522%252Fdev%252Fsda2%2522%253A%257B%2522filesystem%2522%253A%2522LVM2_member%2522%252C%2522size%2522%253A%252215.51%2BGiB%2522%252C%2522size_bytes%2522%253A16654532608%252C%2522uuid%2522%253A%25228JMsR8-Uhdd-tdvv-S7sE-b7Zm-rX7v-d20FVO%2522%257D%257D%252C%2522path%2522%253A%2522%252Fusr%252Flocal%252Fbin%253A%252Froot%252Fbin%253A%252Fusr%252Flocal%252Fsbin%253A%252Fusr%252Flocal%252Fbin%253A%252Fusr%252Fsbin%253A%252Fusr%252Fbin%253A%252Fopt%252Fpuppetlabs%252Fbin%253A%252Fsbin%2522%252C%2522physicalprocessorcount%2522%253A2%252C%2522processor0%2522%253A%2522Intel%2528R%2529%2BXeon%2528R%2529%2BCPU%2BE5-2680%2Bv3%2B%2540%2B2.50GHz%2522%252C%2522processor1%2522%253A%2522Intel%2528R%2529%2BXeon%2528R%2529%2BCPU%2BE5-2680%2Bv3%2B%2540%2B2.50GHz%2522%252C%2522processorcount%2522%253A2%252C%2522processors%2522%253A%257B%2522count%2522%253A2%252C%2522isa%2522%253A%2522x86_64%2522%252C%2522models%2522%253A%255B%2522Intel%2528R%2529%2BXeon%2528R%2529%2BCPU%2BE5-2680%2Bv3%2B%2540%2B2.50GHz%2522%252C%2522Intel%2528R%2529%2BXeon%2528R%2529%2BCPU%2BE5-2680%2Bv3%2B%2540%2B2.50GHz%2522%255D%252C%2522physicalcount%2522%253A2%257D%252C%2522productname%2522%253A%2522VMware%2BVirtual%2BPlatform%2522%252C%2522puppet_vardir%2522%253A%2522%252Fopt%252Fpuppetlabs%252Fpuppet%252Fcache%2522%252C%2522puppetversion%2522%253A%25224.5.2%2522%252C%2522root_home%2522%253A%2522%252Froot%2522%252C%2522rsyslog_version%2522%253A%25227.4.7%2522%252C%2522ruby%2522%253A%257B%2522platform%2522%253A%2522x86_64-linux%2522%252C%2522sitedir%2522%253A%2522%252Fopt%252Fpuppetlabs%252Fpuppet%252Flib%252Fruby%252Fsite_ruby%252F2.1.0%2522%252C%2522version%2522%253A%25222.1.9%2522%257D%252C%2522rubyplatform%2522%253A%2522x86_64-linux%2522%252C%2522rubysitedir%2522%253A%2522%252Fopt%252Fpuppetlabs%252Fpuppet%252Flib%252Fruby%252Fsite_ruby%252F2.1.0%2522%252C%2522rubyversion%2522%253A%25222.1.9%2522%252C%2522selinux%2522%253Atrue%252C%2522selinux_config_mode%2522%253A%2522enforcing%2522%252C%2522selinux_current_mode%2522%253A%2522enforcing%2522%252C%2522selinux_enforced%2522%253Atrue%252C%2522selinux_policyversion%2522%253A%252228%2522%252C%2522serialnumber%2522%253A%2522VMware-42%2B0f%2Bb7%2B8e%2Bad%2B1a%2B55%2Bfc-47%2B6f%2B48%2Bd2%2B98%2B51%2B31%2Ba7%2522%252C%2522service_provider%2522%253A%2522systemd%2522%252C%2522ssh%2522%253A%257B%2522ecdsa%2522%253A%257B%2522fingerprints%2522%253A%257B%2522sha1%2522%253A%2522SSHFP%2B3%2B1%2Bd39b22b2e94886804feff4df669da23cdb358b58%2522%252C%2522sha256%2522%253A%2522SSHFP%2B3%2B2%2B94a8328bce249921a53ffd36cb1d4b7b5a723949abebd554d7d0507e8c83e04c%2522%257D%252C%2522key%2522%253A%2522AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBLqjIT44wYmXSU%252Bvc5zsUUxf00XTllP22tjIwChv3sW7uNhtqLXc1bagOqpRLctiBi64bo%252F%252BCPvBJxUT3%252B5RWOc%253D%2522%257D%252C%2522rsa%2522%253A%257B%2522fingerprints%2522%253A%257B%2522sha1%2522%253A%2522SSHFP%2B1%2B1%2Be0f23ba5ff40fa9bc18c4fbb820c4f6afdef335d%2522%252C%2522sha256%2522%253A%2522SSHFP%2B1%2B2%2B9333a82d26cfb2e1e89368e8061eef1c48a87da677395cf02f0ff1b72d862008%2522%257D%252C%2522key%2522%253A%2522AAAAB3NzaC1yc2EAAAADAQABAAABAQDBKcjgii7T6UEVEUZGYsI52NZmzbJABGJHqnR%252BxeAlCz0H939xVgQHK7%252FnCq3joxmVLwTKd7DTCqMn9x1Q7MJ9ERJWIUCgwPNdS4YDfq52nddk%252BFNtAfhiWsnAjS1MNGUDUcAxakOjvcWp%252FpPG6cGIq8gnnMdY3Nemlq3i3b5zruvv1z77Mq2rPthfibEP5kI3Hwt%252FrrcyIuUWiE5oGh0CBw4hLdB3BZMu0OzPcbKW%252Fn8rM0krYB6t1aC5AJy%252FdIJrFVyjbvI6XFBfYm2eqMM0eZGaerpAt%252Bd%252B%252FcmiFJF2PLAb5HxkeTpbm%252BgaiqfkI8UDsjc77wGvnvC3M3nz%252F6uL%2522%257D%257D%252C%2522sshecdsakey%2522%253A%2522AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBLqjIT44wYmXSU%252Bvc5zsUUxf00XTllP22tjIwChv3sW7uNhtqLXc1bagOqpRLctiBi64bo%252F%252BCPvBJxUT3%252B5RWOc%253D%2522%252C%2522sshfp_ecdsa%2522%253A%2522SSHFP%2B3%2B1%2Bd39b22b2e94886804feff4df669da23cdb358b58%255CnSSHFP%2B3%2B2%2B94a8328bce249921a53ffd36cb1d4b7b5a723949abebd554d7d0507e8c83e04c%2522%252C%2522sshfp_rsa%2522%253A%2522SSHFP%2B1%2B1%2Be0f23ba5ff40fa9bc18c4fbb820c4f6afdef335d%255CnSSHFP%2B1%2B2%2B9333a82d26cfb2e1e89368e8061eef1c48a87da677395cf02f0ff1b72d862008%2522%252C%2522sshrsakey%2522%253A%2522AAAAB3NzaC1yc2EAAAADAQABAAABAQDBKcjgii7T6UEVEUZGYsI52NZmzbJABGJHqnR%252BxeAlCz0H939xVgQHK7%252FnCq3joxmVLwTKd7DTCqMn9x1Q7MJ9ERJWIUCgwPNdS4YDfq52nddk%252BFNtAfhiWsnAjS1MNGUDUcAxakOjvcWp%252FpPG6cGIq8gnnMdY3Nemlq3i3b5zruvv1z77Mq2rPthfibEP5kI3Hwt%252FrrcyIuUWiE5oGh0CBw4hLdB3BZMu0OzPcbKW%252Fn8rM0krYB6t1aC5AJy%252FdIJrFVyjbvI6XFBfYm2eqMM0eZGaerpAt%252Bd%252B%252FcmiFJF2PLAb5HxkeTpbm%252BgaiqfkI8UDsjc77wGvnvC3M3nz%252F6uL%2522%252C%2522staging_http_get%2522%253A%2522curl%2522%252C%2522swapfree%2522%253A%25221.60%2BGiB%2522%252C%2522swapfree_mb%2522%253A1639.99609375%252C%2522swapsize%2522%253A%25221.60%2BGiB%2522%252C%2522swapsize_mb%2522%253A1639.99609375%252C%2522system_uptime%2522%253A%257B%2522days%2522%253A0%252C%2522hours%2522%253A2%252C%2522seconds%2522%253A7529%252C%2522uptime%2522%253A%25222%253A05%2Bhours%2522%257D%252C%2522timezone%2522%253A%2522PDT%2522%252C%2522uptime%2522%253A%25222%253A05%2Bhours%2522%252C%2522uptime_days%2522%253A0%252C%2522uptime_hours%2522%253A2%252C%2522uptime_seconds%2522%253A7529%252C%2522uuid%2522%253A%2522420FB78E-AD1A-55FC-476F-48D2985131A7%2522%252C%2522virtual%2522%253A%2522vmware%2522%252C%2522clientcert%2522%253A%2522x1pqeru6g8miy73.delivery.puppetlabs.net%2522%252C%2522clientversion%2522%253A%25224.5.2%2522%252C%2522clientnoop%2522%253Afalse%257D%252C%2522timestamp%2522%253A%25222016-06-22T11%253A48%253A00.079887501-07%253A00%2522%252C%2522expiration%2522%253A%25222016-06-22T12%253A18%253A00.080184219-07%253A00%2522%257D&configured_environment="
               environment-name
               "&transaction_uuid=0484b5aa-0139-4727-a116-9e49a527446c&static_catalog=true&checksum_type=md5.sha256&fail_on_404=true")
   "params" {"configured_environment" environment-name
             "fail_on_404" "true"
             "code_id" nil
             "facts" (str "%7B%22name%22%3A%22" node-name "%22%2C"
                          "%22values%22%3A%7B"
                          "%22agent_specified_environment%22%3A%22"
                          environment-name
                          "%22%2C"
                          "%22concat_basedir%22%3A%22"
                          (-> jruby-puppet-config
                              :master-var-dir
                              (fs/file "concat")
                              (fs/normalized)
                              str)
                          "%22%2C%22whereami%22%3A%22pdx%22%2C"
                          "%22aio_agent_version%22%3A%221.5.2%22%2C%22architecture%22%3A%22x86_64%22%2C%22augeas%22%3A%7B%22version%22%3A%221.4.0%22%7D%2C%22augeasversion%22%3A%221.4.0%22%2C%22bios_release_date%22%3A%2207%2F30%2F2013%22%2C%22bios_vendor%22%3A%22Phoenix+Technologies+LTD%22%2C%22bios_version%22%3A%226.00%22%2C%22blockdevice_fd0_size%22%3A4096%2C%22blockdevice_sda_model%22%3A%22Virtual+disk%22%2C%22blockdevice_sda_size%22%3A17179869184%2C%22blockdevice_sda_vendor%22%3A%22VMware%22%2C%22blockdevice_sr0_model%22%3A%22VMware+IDE+CDR10%22%2C%22blockdevice_sr0_size%22%3A1073741312%2C%22blockdevice_sr0_vendor%22%3A%22NECVMWar%22%2C%22blockdevices%22%3A%22fd0%2Csda%2Csr0%22%2C%22boardmanufacturer%22%3A%22Intel+Corporation%22%2C%22boardproductname%22%3A%22440BX+Desktop+Reference+Platform%22%2C%22boardserialnumber%22%3A%22None%22%2C%22chassisassettag%22%3A%22No+Asset+Tag%22%2C%22chassistype%22%3A%22Other%22%2C%22choco_install_path%22%3A%22C%3A%5C%5CProgramData%5C%5Cchocolatey%22%2C%22chocolateyversion%22%3A%220%22%2C%22dhcp_servers%22%3A%7B%22ens160%22%3A%2210.32.22.10%22%2C%22system%22%3A%2210.32.22.10%22%7D%2C%22disks%22%3A%7B%22fd0%22%3A%7B%22size%22%3A%224.00+KiB%22%2C%22size_bytes%22%3A4096%7D%2C%22sda%22%3A%7B%22model%22%3A%22Virtual+disk%22%2C%22size%22%3A%2216.00+GiB%22%2C%22size_bytes%22%3A17179869184%2C%22vendor%22%3A%22VMware%22%7D%2C%22sr0%22%3A%7B%22model%22%3A%22VMware+IDE+CDR10%22%2C%22size%22%3A%221.00+GiB%22%2C%22size_bytes%22%3A1073741312%2C%22vendor%22%3A%22NECVMWar%22%7D%7D%2C%22dmi%22%3A%7B%22bios%22%3A%7B%22release_date%22%3A%2207%2F30%2F2013%22%2C%22vendor%22%3A%22Phoenix+Technologies+LTD%22%2C%22version%22%3A%226.00%22%7D%2C%22board%22%3A%7B%22manufacturer%22%3A%22Intel+Corporation%22%2C%22product%22%3A%22440BX+Desktop+Reference+Platform%22%2C%22serial_number%22%3A%22None%22%7D%2C%22chassis%22%3A%7B%22asset_tag%22%3A%22No+Asset+Tag%22%2C%22type%22%3A%22Other%22%7D%2C%22manufacturer%22%3A%22VMware%2C+Inc.%22%2C%22product%22%3A%7B%22name%22%3A%22VMware+Virtual+Platform%22%2C%22serial_number%22%3A%22VMware-42+0f+b7+8e+ad+1a+55+fc-47+6f+48+d2+98+51+31+a7%22%2C%22uuid%22%3A%22420FB78E-AD1A-55FC-476F-48D2985131A7%22%7D%7D%2C%22domain%22%3A%22delivery.puppetlabs.net%22%2C%22facterversion%22%3A%223.2.0%22%2C%22filesystems%22%3A%22xfs%22%2C%22fqdn%22%3A%22x1pqeru6g8miy73.delivery.puppetlabs.net%22%2C%22gid%22%3A%22root%22%2C%22hardwareisa%22%3A%22x86_64%22%2C%22hardwaremodel%22%3A%22x86_64%22%2C%22hostname%22%3A%22x1pqeru6g8miy73%22%2C%22id%22%3A%22root%22%2C%22identity%22%3A%7B%22gid%22%3A0%2C%22group%22%3A%22root%22%2C%22uid%22%3A0%2C%22user%22%3A%22root%22%7D%2C%22interfaces%22%3A%22ens160%2Clo%22%2C%22ip6tables_version%22%3A%221.4.21%22%2C%22ipaddress%22%3A%2210.32.116.16%22%2C%22ipaddress6%22%3A%22fe80%3A%3A250%3A56ff%3Afe8f%3A8e97%22%2C%22ipaddress6_ens160%22%3A%22fe80%3A%3A250%3A56ff%3Afe8f%3A8e97%22%2C%22ipaddress6_lo%22%3A%22%3A%3A1%22%2C%22ipaddress_ens160%22%3A%2210.32.116.16%22%2C%22ipaddress_lo%22%3A%22127.0.0.1%22%2C%22iptables_version%22%3A%221.4.21%22%2C%22is_pe%22%3Afalse%2C%22is_virtual%22%3Atrue%2C%22java_default_home%22%3A%22%2Fusr%2Flib%2Fjvm%2Fjava-1.7.0-openjdk-1.7.0.101-2.6.6.1.el7_2.x86_64%22%2C%22java_libjvm_path%22%3A%22%2Fusr%2Flib%2Fjvm%2Fjava-1.7.0-openjdk-1.7.0.101-2.6.6.1.el7_2.x86_64%2Fjre%2Flib%2Famd64%2Fserver%22%2C%22java_major_version%22%3A%227%22%2C%22java_patch_level%22%3A%22101%22%2C%22java_version%22%3A%221.7.0_101%22%2C%22jenkins_plugins%22%3A%22%22%2C%22kernel%22%3A%22Linux%22%2C%22kernelmajversion%22%3A%223.10%22%2C%22kernelrelease%22%3A%223.10.0-123.4.2.el7.x86_64%22%2C%22kernelversion%22%3A%223.10.0%22%2C%22load_averages%22%3A%7B%2215m%22%3A0.05%2C%221m%22%3A0.09%2C%225m%22%3A0.04%7D%2C%22macaddress%22%3A%2200%3A50%3A56%3A8f%3A8e%3A97%22%2C%22macaddress_ens160%22%3A%2200%3A50%3A56%3A8f%3A8e%3A97%22%2C%22manufacturer%22%3A%22VMware%2C+Inc.%22%2C%22memory%22%3A%7B%22swap%22%3A%7B%22available%22%3A%221.60+GiB%22%2C%22available_bytes%22%3A1719660544%2C%22capacity%22%3A%220%25%22%2C%22total%22%3A%221.60+GiB%22%2C%22total_bytes%22%3A1719660544%2C%22used%22%3A%220+bytes%22%2C%22used_bytes%22%3A0%7D%2C%22system%22%3A%7B%22available%22%3A%223.30+GiB%22%2C%22available_bytes%22%3A3541131264%2C%22capacity%22%3A%2210.96%25%22%2C%22total%22%3A%223.70+GiB%22%2C%22total_bytes%22%3A3976998912%2C%22used%22%3A%22415.68+MiB%22%2C%22used_bytes%22%3A435867648%7D%7D%2C%22memoryfree%22%3A%223.30+GiB%22%2C%22memoryfree_mb%22%3A3377.0859375%2C%22memorysize%22%3A%223.70+GiB%22%2C%22memorysize_mb%22%3A3792.76171875%2C%22mountpoints%22%3A%7B%22%2F%22%3A%7B%22available%22%3A%2212.24+GiB%22%2C%22available_bytes%22%3A13137911808%2C%22capacity%22%3A%2211.95%25%22%2C%22device%22%3A%22%2Fdev%2Fmapper%2Fcentos-root%22%2C%22filesystem%22%3A%22xfs%22%2C%22options%22%3A%5B%22rw%22%2C%22seclabel%22%2C%22relatime%22%2C%22attr2%22%2C%22inode64%22%2C%22noquota%22%5D%2C%22size%22%3A%2213.90+GiB%22%2C%22size_bytes%22%3A14921236480%2C%22used%22%3A%221.66+GiB%22%2C%22used_bytes%22%3A1783324672%7D%2C%22%2Fboot%22%3A%7B%22available%22%3A%22371.50+MiB%22%2C%22available_bytes%22%3A389541888%2C%22capacity%22%3A%2225.20%25%22%2C%22device%22%3A%22%2Fdev%2Fsda1%22%2C%22filesystem%22%3A%22xfs%22%2C%22options%22%3A%5B%22rw%22%2C%22seclabel%22%2C%22relatime%22%2C%22attr2%22%2C%22inode64%22%2C%22noquota%22%5D%2C%22size%22%3A%22496.67+MiB%22%2C%22size_bytes%22%3A520794112%2C%22used%22%3A%22125.17+MiB%22%2C%22used_bytes%22%3A131252224%7D%7D%2C%22mtu_ens160%22%3A1500%2C%22mtu_lo%22%3A65536%2C%22netmask%22%3A%22255.255.240.0%22%2C%22netmask6%22%3A%22ffff%3Affff%3Affff%3Affff%3A%3A%22%2C%22netmask6_ens160%22%3A%22ffff%3Affff%3Affff%3Affff%3A%3A%22%2C%22netmask6_lo%22%3A%22ffff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%22%2C%22netmask_ens160%22%3A%22255.255.240.0%22%2C%22netmask_lo%22%3A%22255.0.0.0%22%2C%22network%22%3A%2210.32.112.0%22%2C%22network6%22%3A%22fe80%3A%3A%22%2C%22network6_ens160%22%3A%22fe80%3A%3A%22%2C%22network6_lo%22%3A%22%3A%3A1%22%2C%22network_ens160%22%3A%2210.32.112.0%22%2C%22network_lo%22%3A%22127.0.0.0%22%2C%22networking%22%3A%7B%22dhcp%22%3A%2210.32.22.10%22%2C%22domain%22%3A%22delivery.puppetlabs.net%22%2C%22fqdn%22%3A%22x1pqeru6g8miy73.delivery.puppetlabs.net%22%2C%22hostname%22%3A%22x1pqeru6g8miy73%22%2C%22interfaces%22%3A%7B%22ens160%22%3A%7B%22bindings%22%3A%5B%7B%22address%22%3A%2210.32.116.16%22%2C%22netmask%22%3A%22255.255.240.0%22%2C%22network%22%3A%2210.32.112.0%22%7D%5D%2C%22bindings6%22%3A%5B%7B%22address%22%3A%22fe80%3A%3A250%3A56ff%3Afe8f%3A8e97%22%2C%22netmask%22%3A%22ffff%3Affff%3Affff%3Affff%3A%3A%22%2C%22network%22%3A%22fe80%3A%3A%22%7D%5D%2C%22dhcp%22%3A%2210.32.22.10%22%2C%22ip%22%3A%2210.32.116.16%22%2C%22ip6%22%3A%22fe80%3A%3A250%3A56ff%3Afe8f%3A8e97%22%2C%22mac%22%3A%2200%3A50%3A56%3A8f%3A8e%3A97%22%2C%22mtu%22%3A1500%2C%22netmask%22%3A%22255.255.240.0%22%2C%22netmask6%22%3A%22ffff%3Affff%3Affff%3Affff%3A%3A%22%2C%22network%22%3A%2210.32.112.0%22%2C%22network6%22%3A%22fe80%3A%3A%22%7D%2C%22lo%22%3A%7B%22bindings%22%3A%5B%7B%22address%22%3A%22127.0.0.1%22%2C%22netmask%22%3A%22255.0.0.0%22%2C%22network%22%3A%22127.0.0.0%22%7D%5D%2C%22bindings6%22%3A%5B%7B%22address%22%3A%22%3A%3A1%22%2C%22netmask%22%3A%22ffff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%22%2C%22network%22%3A%22%3A%3A1%22%7D%5D%2C%22ip%22%3A%22127.0.0.1%22%2C%22ip6%22%3A%22%3A%3A1%22%2C%22mtu%22%3A65536%2C%22netmask%22%3A%22255.0.0.0%22%2C%22netmask6%22%3A%22ffff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%3Affff%22%2C%22network%22%3A%22127.0.0.0%22%2C%22network6%22%3A%22%3A%3A1%22%7D%7D%2C%22ip%22%3A%2210.32.116.16%22%2C%22ip6%22%3A%22fe80%3A%3A250%3A56ff%3Afe8f%3A8e97%22%2C%22mac%22%3A%2200%3A50%3A56%3A8f%3A8e%3A97%22%2C%22mtu%22%3A1500%2C%22netmask%22%3A%22255.255.240.0%22%2C%22netmask6%22%3A%22ffff%3Affff%3Affff%3Affff%3A%3A%22%2C%22network%22%3A%2210.32.112.0%22%2C%22network6%22%3A%22fe80%3A%3A%22%2C%22primary%22%3A%22ens160%22%7D%2C%22operatingsystem%22%3A%22CentOS%22%2C%22operatingsystemmajrelease%22%3A%227%22%2C%22operatingsystemrelease%22%3A%227.0.1406%22%2C%22os%22%3A%7B%22architecture%22%3A%22x86_64%22%2C%22family%22%3A%22RedHat%22%2C%22hardware%22%3A%22x86_64%22%2C%22name%22%3A%22CentOS%22%2C%22release%22%3A%7B%22full%22%3A%227.0.1406%22%2C%22major%22%3A%227%22%2C%22minor%22%3A%220%22%7D%2C%22selinux%22%3A%7B%22config_mode%22%3A%22enforcing%22%2C%22current_mode%22%3A%22enforcing%22%2C%22enabled%22%3Atrue%2C%22enforced%22%3Atrue%2C%22policy_version%22%3A%2228%22%7D%7D%2C%22osfamily%22%3A%22RedHat%22%2C%22package_provider%22%3A%22yum%22%2C%22partitions%22%3A%7B%22%2Fdev%2Fmapper%2Fcentos-root%22%3A%7B%22filesystem%22%3A%22xfs%22%2C%22mount%22%3A%22%2F%22%2C%22size%22%3A%2213.91+GiB%22%2C%22size_bytes%22%3A14931722240%2C%22uuid%22%3A%223e1d8d4e-2216-4563-8d58-b433c8faf8ce%22%7D%2C%22%2Fdev%2Fmapper%2Fcentos-swap%22%3A%7B%22filesystem%22%3A%22swap%22%2C%22size%22%3A%221.60+GiB%22%2C%22size_bytes%22%3A1719664640%2C%22uuid%22%3A%2286cb57f4-9390-448b-867e-35c3ebcf8238%22%7D%2C%22%2Fdev%2Fsda1%22%3A%7B%22filesystem%22%3A%22xfs%22%2C%22mount%22%3A%22%2Fboot%22%2C%22size%22%3A%22500.00+MiB%22%2C%22size_bytes%22%3A524288000%2C%22uuid%22%3A%224a9725cc-739a-45f5-8ea0-a83885eaeea0%22%7D%2C%22%2Fdev%2Fsda2%22%3A%7B%22filesystem%22%3A%22LVM2_member%22%2C%22size%22%3A%2215.51+GiB%22%2C%22size_bytes%22%3A16654532608%2C%22uuid%22%3A%228JMsR8-Uhdd-tdvv-S7sE-b7Zm-rX7v-d20FVO%22%7D%7D%2C%22path%22%3A%22%2Fusr%2Flocal%2Fbin%3A%2Froot%2Fbin%3A%2Fusr%2Flocal%2Fsbin%3A%2Fusr%2Flocal%2Fbin%3A%2Fusr%2Fsbin%3A%2Fusr%2Fbin%3A%2Fopt%2Fpuppetlabs%2Fbin%3A%2Fsbin%22%2C%22physicalprocessorcount%22%3A2%2C%22processor0%22%3A%22Intel%28R%29+Xeon%28R%29+CPU+E5-2680+v3+%40+2.50GHz%22%2C%22processor1%22%3A%22Intel%28R%29+Xeon%28R%29+CPU+E5-2680+v3+%40+2.50GHz%22%2C%22processorcount%22%3A2%2C%22processors%22%3A%7B%22count%22%3A2%2C%22isa%22%3A%22x86_64%22%2C%22models%22%3A%5B%22Intel%28R%29+Xeon%28R%29+CPU+E5-2680+v3+%40+2.50GHz%22%2C%22Intel%28R%29+Xeon%28R%29+CPU+E5-2680+v3+%40+2.50GHz%22%5D%2C%22physicalcount%22%3A2%7D%2C%22productname%22%3A%22VMware+Virtual+Platform%22%2C%22puppet_vardir%22%3A%22%2Fopt%2Fpuppetlabs%2Fpuppet%2Fcache%22%2C%22puppetversion%22%3A%224.5.2%22%2C%22root_home%22%3A%22%2Froot%22%2C%22rsyslog_version%22%3A%227.4.7%22%2C%22ruby%22%3A%7B%22platform%22%3A%22x86_64-linux%22%2C%22sitedir%22%3A%22%2Fopt%2Fpuppetlabs%2Fpuppet%2Flib%2Fruby%2Fsite_ruby%2F2.1.0%22%2C%22version%22%3A%222.1.9%22%7D%2C%22rubyplatform%22%3A%22x86_64-linux%22%2C%22rubysitedir%22%3A%22%2Fopt%2Fpuppetlabs%2Fpuppet%2Flib%2Fruby%2Fsite_ruby%2F2.1.0%22%2C%22rubyversion%22%3A%222.1.9%22%2C%22selinux%22%3Atrue%2C%22selinux_config_mode%22%3A%22enforcing%22%2C%22selinux_current_mode%22%3A%22enforcing%22%2C%22selinux_enforced%22%3Atrue%2C%22selinux_policyversion%22%3A%2228%22%2C%22serialnumber%22%3A%22VMware-42+0f+b7+8e+ad+1a+55+fc-47+6f+48+d2+98+51+31+a7%22%2C%22service_provider%22%3A%22systemd%22%2C%22ssh%22%3A%7B%22ecdsa%22%3A%7B%22fingerprints%22%3A%7B%22sha1%22%3A%22SSHFP+3+1+d39b22b2e94886804feff4df669da23cdb358b58%22%2C%22sha256%22%3A%22SSHFP+3+2+94a8328bce249921a53ffd36cb1d4b7b5a723949abebd554d7d0507e8c83e04c%22%7D%2C%22key%22%3A%22AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBLqjIT44wYmXSU%2Bvc5zsUUxf00XTllP22tjIwChv3sW7uNhtqLXc1bagOqpRLctiBi64bo%2F%2BCPvBJxUT3%2B5RWOc%3D%22%7D%2C%22rsa%22%3A%7B%22fingerprints%22%3A%7B%22sha1%22%3A%22SSHFP+1+1+e0f23ba5ff40fa9bc18c4fbb820c4f6afdef335d%22%2C%22sha256%22%3A%22SSHFP+1+2+9333a82d26cfb2e1e89368e8061eef1c48a87da677395cf02f0ff1b72d862008%22%7D%2C%22key%22%3A%22AAAAB3NzaC1yc2EAAAADAQABAAABAQDBKcjgii7T6UEVEUZGYsI52NZmzbJABGJHqnR%2BxeAlCz0H939xVgQHK7%2FnCq3joxmVLwTKd7DTCqMn9x1Q7MJ9ERJWIUCgwPNdS4YDfq52nddk%2BFNtAfhiWsnAjS1MNGUDUcAxakOjvcWp%2FpPG6cGIq8gnnMdY3Nemlq3i3b5zruvv1z77Mq2rPthfibEP5kI3Hwt%2FrrcyIuUWiE5oGh0CBw4hLdB3BZMu0OzPcbKW%2Fn8rM0krYB6t1aC5AJy%2FdIJrFVyjbvI6XFBfYm2eqMM0eZGaerpAt%2Bd%2B%2FcmiFJF2PLAb5HxkeTpbm%2BgaiqfkI8UDsjc77wGvnvC3M3nz%2F6uL%22%7D%7D%2C%22sshecdsakey%22%3A%22AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBLqjIT44wYmXSU%2Bvc5zsUUxf00XTllP22tjIwChv3sW7uNhtqLXc1bagOqpRLctiBi64bo%2F%2BCPvBJxUT3%2B5RWOc%3D%22%2C%22sshfp_ecdsa%22%3A%22SSHFP+3+1+d39b22b2e94886804feff4df669da23cdb358b58%5CnSSHFP+3+2+94a8328bce249921a53ffd36cb1d4b7b5a723949abebd554d7d0507e8c83e04c%22%2C%22sshfp_rsa%22%3A%22SSHFP+1+1+e0f23ba5ff40fa9bc18c4fbb820c4f6afdef335d%5CnSSHFP+1+2+9333a82d26cfb2e1e89368e8061eef1c48a87da677395cf02f0ff1b72d862008%22%2C%22sshrsakey%22%3A%22AAAAB3NzaC1yc2EAAAADAQABAAABAQDBKcjgii7T6UEVEUZGYsI52NZmzbJABGJHqnR%2BxeAlCz0H939xVgQHK7%2FnCq3joxmVLwTKd7DTCqMn9x1Q7MJ9ERJWIUCgwPNdS4YDfq52nddk%2BFNtAfhiWsnAjS1MNGUDUcAxakOjvcWp%2FpPG6cGIq8gnnMdY3Nemlq3i3b5zruvv1z77Mq2rPthfibEP5kI3Hwt%2FrrcyIuUWiE5oGh0CBw4hLdB3BZMu0OzPcbKW%2Fn8rM0krYB6t1aC5AJy%2FdIJrFVyjbvI6XFBfYm2eqMM0eZGaerpAt%2Bd%2B%2FcmiFJF2PLAb5HxkeTpbm%2BgaiqfkI8UDsjc77wGvnvC3M3nz%2F6uL%22%2C%22staging_http_get%22%3A%22curl%22%2C%22swapfree%22%3A%221.60+GiB%22%2C%22swapfree_mb%22%3A1639.99609375%2C%22swapsize%22%3A%221.60+GiB%22%2C%22swapsize_mb%22%3A1639.99609375%2C%22system_uptime%22%3A%7B%22days%22%3A0%2C%22hours%22%3A2%2C%22seconds%22%3A7529%2C%22uptime%22%3A%222%3A05+hours%22%7D%2C%22timezone%22%3A%22PDT%22%2C%22uptime%22%3A%222%3A05+hours%22%2C%22uptime_days%22%3A0%2C%22uptime_hours%22%3A2%2C%22uptime_seconds%22%3A7529%2C%22uuid%22%3A%22420FB78E-AD1A-55FC-476F-48D2985131A7%22%2C%22virtual%22%3A%22vmware%22%2C%22clientcert%22%3A%22x1pqeru6g8miy73.delivery.puppetlabs.net%22%2C%22clientversion%22%3A%224.5.2%22%2C%22clientnoop%22%3Afalse%7D%2C%22timestamp%22%3A%222016-06-22T11%3A48%3A00.079887501-07%3A00%22%2C%22expiration%22%3A%222016-06-22T12%3A18%3A00.080184219-07%3A00%22%7D")
             "rest" node-name
             "transaction_uuid" "0484b5aa-0139-4727-a116-9e49a527446c"
             "static_catalog" "true"
             "checksum_type" "md5.sha256"
             "environment" environment-name
             "facts_format" "pson"}
   "request-method" "POST"
   "uri" (format "/puppet/v3/catalog/%s" node-name)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate create-scripting-container :- ScriptingContainer
  [{:keys [ruby-load-path gem-home compile-mode]}
   :- jruby-schemas/JRubyPuppetConfig]
  (doto (jruby-puppet-internal/empty-scripting-container
         (cons "classpath:/puppetserver-lib" ruby-load-path)
         gem-home
         compile-mode)
    (.runScriptlet "require 'jar-dependencies'")))

(schema/defn ^:always-validate initialize-puppet-in-container :- JRubyPuppet
  [scripting-container :- ScriptingContainer
   {:keys [http-client-ssl-protocols http-client-cipher-suites
           http-client-connect-timeout-milliseconds
           http-client-idle-timeout-milliseconds
           use-legacy-auth-conf] :as config} :- jruby-schemas/JRubyPuppetConfig]
  (.runScriptlet scripting-container "require 'puppet/server/master'")
  (let [env-registry (puppet-env/environment-registry)
        ruby-puppet-class (.runScriptlet scripting-container "Puppet::Server::Master")
        puppet-config (jruby-internal/config->puppet-config config)
        puppetserver-config (HashMap.)]
    (when http-client-ssl-protocols
      (.put puppetserver-config "ssl_protocols" (into-array String http-client-ssl-protocols)))
    (when http-client-cipher-suites
      (.put puppetserver-config "cipher_suites" (into-array String http-client-cipher-suites)))
    (.put puppetserver-config "profiler" nil)
    (.put puppetserver-config "environment_registry" env-registry)
    (.put puppetserver-config "http_connect_timeout_milliseconds"
          http-client-connect-timeout-milliseconds)
    (.put puppetserver-config "http_idle_timeout_milliseconds"
          http-client-idle-timeout-milliseconds)
    (.put puppetserver-config "use_legacy_auth_conf" use-legacy-auth-conf)
    (.callMethodWithArgArray
     scripting-container
     ruby-puppet-class
     "new"
     (into-array Object
                 [puppet-config puppetserver-config])
     JRubyPuppet)))

(schema/defn ^:always-validate create-jruby-puppet-container
  [config :- jruby-schemas/JRubyPuppetConfig]
  (let [scripting-container (create-scripting-container config)
        jruby-puppet (initialize-puppet-in-container
                      scripting-container
                      config)]
    {:container scripting-container
     :jruby-puppet jruby-puppet}))

(schema/defn ^:always-validate terminate-jruby-puppet-container
  [{:keys [jruby-puppet container]} :- memmeasure-schemas/JRubyPuppetContainer]
  (.terminate jruby-puppet)
  (.terminate container))

(defmacro with-jruby-puppet
  [jruby-puppet config & body]
  `(let [jruby-puppet-container# (create-jruby-puppet-container ~config)
         ~jruby-puppet (:jruby-puppet jruby-puppet-container#)]
     (try
       ~@body
       (finally
         (terminate-jruby-puppet-container jruby-puppet-container#)))))

(schema/defn ^:always-validate create-jruby-puppet-containers :-
  [memmeasure-schemas/JRubyPuppetContainer]
  [size :- schema/Int
   config :- jruby-schemas/JRubyPuppetConfig]
  (log/infof "Creating %d JRubyPuppet container%s"
             size
             (if (= 1 size) "" "s"))
  (let [containers
        (doall (for [cnt (range size)]
                 (do
                   (log/infof "Creating JRubyPuppet container %d of %d"
                              (inc cnt)
                              size)
                   (create-jruby-puppet-container config))))]
    (log/infof "Finished creating %d JRubyPuppet container%s"
               size
               (if (= 1 size) "" "s"))
    containers))

(schema/defn ^:always-validate terminate-jruby-puppet-containers
  [pool :- [memmeasure-schemas/JRubyPuppetContainer]]
  (doseq [pool-instance pool]
    (terminate-jruby-puppet-container pool-instance)))

(defmacro with-jruby-puppets
  [jruby-puppets size config & body]
  `(let [jruby-puppet-containers# (create-jruby-puppet-containers
                                   ~size
                                   ~config)
         ~jruby-puppets (map :jruby-puppet jruby-puppet-containers#)]
     (try
       ~@body
       (finally
         (terminate-jruby-puppet-containers jruby-puppet-containers#)))))

(defmacro with-environments
  [environments size base-environment-name master-code-dir & body]
  `(let [environment-dir# (fs/file ~master-code-dir "environments")
         ~environments
          (let [base-environment-dir# (fs/file environment-dir#
                                               ~base-environment-name)]
            (doall
             (for [cnt# (range ~size)
                   :let [copy-environment-name# (str ~base-environment-name
                                                     "_"
                                                     (inc cnt#))
                         copy-environment-dir# (fs/file environment-dir#
                                                        copy-environment-name#)]]
               (do
                 (fs/delete-dir copy-environment-dir#)
                 (fs/copy-dir base-environment-dir# copy-environment-dir#)
                 copy-environment-name#))))]
     (try
       ~@body
       (finally
         (doseq [copy-environment-name# ~environments]
           (let [copy-environment-dir# (fs/file environment-dir#
                                                copy-environment-name#)]
             (fs/delete-dir copy-environment-dir#)))))))

(schema/defn ^:always-validate get-catalog :- {schema/Keyword schema/Any}
  [jruby-puppet :- JRubyPuppet
   catalog-output-file :- File
   node-name :- schema/Str
   environment-name :- schema/Str
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   validate-class-name :- (schema/maybe schema/Str)]
  (let [catalog-response
        (request-handler-core/response->map
         (.handleRequest jruby-puppet (HashMap.
                                       (catalog-request node-name
                                                        environment-name
                                                        jruby-puppet-config))))]
    (cheshire/generate-stream catalog-response
                              (io/writer catalog-output-file))
    (log/infof "Catalog written to: %s" (.getCanonicalPath
                                         catalog-output-file))
    (if (= (:status catalog-response) 200)
      (if (and validate-class-name
               (not (-> catalog-response
                        :body
                        cheshire/parse-string
                        (get "classes")
                        ((partial some #(= validate-class-name %))))))
        (throw (Exception.
                (str "Sentinel class name ("
                     validate-class-name
                     ") not found in catalog, body: "
                     (:body catalog-response)))))
      (let [error-message (str "Error getting catalog: status: "
                               (:status catalog-response)
                               ", body: "
                               (:body catalog-response))]
        (throw (Exception. error-message))))
    catalog-response))

(schema/defn ^:always-validate take-yourkit-snapshot! :- schema/Int
  [snapshot-output-dir :- File
   snapshot-base-name :- schema/Str]
  (let [target-snapshot-file (fs/file snapshot-output-dir
                                      (str snapshot-base-name ".snapshot"))
        controller (doto (Controller.)
                     ;; Force GC twice to try to free up some
                     ;; WeakReferences before taking a snapshot.
                     (.forceGC)
                     (.forceGC))
        initial-snapshot-file (.captureMemorySnapshot controller)]
    (log/debugf "Snapshot captured to: %s" initial-snapshot-file)
    (fs/rename initial-snapshot-file target-snapshot-file)
    (log/infof "Snapshot renamed to: %s" (.getCanonicalPath target-snapshot-file))
    (-> target-snapshot-file
        (MemorySnapshot.)
        (.getShallowSize
         "<reachable-objects>
           <from>
             <roots/>
           </from>
           <object-filter>
             <not>
               <objects class=\"java.lang.ref.Finalizer\"/>
             </not>
           </object-filter>
           <field-filter>
             <class name=\"java.lang.ref.Reference\">
               <forbidden field=\"referent\"/>
             </class>
           </field-filter>
         </reachable-objects>"))))

(schema/defn ^:always-validate set-env-timeout!
  [environment-conf-dir :- File
   timeout :- memmeasure-schemas/EnvironmentTimeout]
  (let [environment-conf-file (fs/file environment-conf-dir
                                       "environment.conf")
        lines-without-env-timeout
        (if (fs/readable? environment-conf-file)
          (->> environment-conf-file
               ks/lines
               (remove (partial re-find #"environment_timeout"))
               (str/join "\n")
               (format "%s\n"))
          (do
            (ks/mkdirs! (fs/parent environment-conf-dir))
            ""))]
    (spit environment-conf-file
          (format "%senvironment_timeout=%s\n"
                  lines-without-env-timeout
                  timeout))))


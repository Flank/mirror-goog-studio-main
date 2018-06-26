package com.android.tools.deployer;

import java.util.List;

public interface AdbClient {
    void shell(String[] parameters) throws DeployerException;

    void pull(String srcDirectory, String dstDirectory) throws DeployerException;

    void installMultiple(List<Apk> apks) throws DeployerException;
}

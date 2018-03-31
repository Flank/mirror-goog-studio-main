package com.android.ide.common.resources;

import static com.android.SdkConstants.FD_RES;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fixture for creating resource repositories for tests.
 */
public class ResourceRepositoryFixture {
    private static final Logger LOG = Logger.getLogger(ResourceRepositoryFixture.class.getSimpleName());

    private final Set<File> createdDirectories = new HashSet<>();

    /**
     * Initializes the fixture. Typically, should be called from your TestCase.setUp() method.
     *
     * @throws Exception any exception thrown during initializing
     */
    public void setUp() throws Exception {
        // No-op for now.
    }

    /**
     * Destroys the fixture. Typically, should be called from your TestCase.tearDown() method.
     *
     * @throws Exception any exception thrown during destroying
     */
    public void tearDown() throws Exception {
        List<String> errors = new ArrayList<>();
        for (File dir : createdDirectories) {
            try {
                FileUtils.deletePath(dir);
            } catch (IOException e) {
                errors.add(e.getMessage());
            }
        }
        createdDirectories.clear();
        if (!errors.isEmpty()) {
            fail("Some temporary directories were not deleted:\n" + Joiner.on('\n').join(errors));
        }
    }

    /**
     * Creates a {@link MergerResourceRepository} for a resource folder whose contents is identified
     * by the pairs of relative paths and file contents
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    public MergerResourceRepository createTestResources(
            @NonNull ResourceNamespace namespace, @NonNull Object[] data) throws IOException {
        File dir = TestUtils.createTempDirDeletedOnExit();
        createdDirectories.add(dir);
        File res = new File(dir, FD_RES);
        res.mkdirs();

        assertTrue("Expected even number of items (path,contents)", data.length % 2 == 0);
        for (int i = 0; i < data.length; i += 2) {
            Object relativePathObject = data[i];
            assertTrue(relativePathObject instanceof String);
            String relativePath = (String) relativePathObject;
            relativePath = relativePath.replace('/', File.separatorChar);
            File file = new File(res, relativePath);
            File parent = file.getParentFile();
            parent.mkdirs();

            Object fileContents = data[i + 1];
            if (fileContents instanceof String) {
                String text = (String) fileContents;
                Files.write(text, file, Charsets.UTF_8);
            } else if (fileContents instanceof byte[]) {
                byte[] bytes = (byte[]) fileContents;
                Files.write(bytes, file);
            } else {
                fail("File contents must be Strings or byte[]'s");
            }
        }

        File resFolder = new File(dir, FD_RES);

        ResourceMerger merger = new ResourceMerger(0);
        ResourceSet resourceSet = new ResourceSet("main", namespace, null, false);
        resourceSet.addSource(resFolder);
        resourceSet.setTrackSourcePositions(false);
        try {
          resourceSet.loadFromFiles(new RecordingLogger());
        } catch (MergingException e) {
          LOG.warning(e.getMessage());
        }
        merger.addDataSet(resourceSet);

        MergerResourceRepository repository = new MergerResourceRepository();
        repository.update(merger);

        return repository;
    }
}

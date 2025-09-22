const webpack = require('webpack');
const path = require('path');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const GitRevisionPlugin = require('git-revision-webpack-plugin');
const CleanWebpackPlugin = require('clean-webpack-plugin');

const config = {
    entry: {
        main: ['./src/js/main.js'],
        room: ['./src/js/room.js'],
        about: ['./src/js/about.js']
    },
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'static/js/[name].[git-revision-hash].js'
    },
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: ['env', 'stage-2']
                    }
                }
            },
            {
                test: /\.vue$/,
                exclude: /node_modules/,
                use: {
                    loader: 'vue-loader'
                }
            },
            {
                test: /\.css$/,
                loader: ExtractTextPlugin.extract({
                    fallback: 'style-loader',
                    use: ['css-loader']
                })
            },
            {
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract({
                    fallback: 'style-loader',
                    use: [
                        'css-loader',
                        {
                            loader: 'sass-loader',
                            options: {
                                implementation: require('sass')
                            }
                        }
                    ]
                })
            }
        ]
    },
    plugins: [
        new CleanWebpackPlugin(['dist']),
        new GitRevisionPlugin(),
        new webpack.optimize.CommonsChunkPlugin({
            name: 'commons',
            minChunks: 2
        }),
        new ExtractTextPlugin({
            filename: 'static/css/[name].[git-revision-hash].css'
        })
    ]
};

module.exports = config;
